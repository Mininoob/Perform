package com.perform.reader.sync

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.palette.graphics.Palette
import com.perform.reader.library.BookDao
import com.perform.reader.library.BookEntity
import com.perform.reader.library.BookFormat
import com.perform.reader.library.BookSource
import com.perform.reader.library.CoverExtractor
import com.perform.reader.library.FolderEntity
import com.perform.reader.library.GENRE_SEPARATOR
import com.perform.reader.library.LibraryBookEntity
import com.perform.sync.CatalogBook
import com.perform.sync.Format
import com.perform.sync.LookupQuery
import com.perform.sync.OrganizeChange
import com.perform.sync.PairRequest
import com.perform.sync.PairingInvite
import com.perform.sync.Protocol
import com.perform.sync.contentIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface SyncStatus {
    data object NotPaired : SyncStatus
    data object Idle : SyncStatus
    data object Syncing : SyncStatus

    /** The last sync didn't reach that library; books already downloaded still open. */
    data class Offline(val reason: String) : SyncStatus
}

/**
 * Keeps the phone's copy of each paired library current: their catalogs with covers,
 * books downloaded when first opened (or all of them, for a library on full sync), the
 * space downloads take, and the phone's own changes and books sent the other way.
 *
 * The same book in two libraries is one book here: one download, one place in the
 * reading, with a membership row per library holding its folder there.
 */
class LibrarySync(
    private val context: Context,
    private val dao: BookDao,
    val settings: SyncSettings,
    private val covers: CoverExtractor,
) {
    private val coverDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val bookDir = File(context.filesDir, "library").apply { mkdirs() }

    /** One sync at a time; launch, pull-to-refresh and the background job can overlap. */
    private val lock = Mutex()

    private val _statuses = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())

    /** Each library's last sync result, by id. */
    val statuses: StateFlow<Map<String, SyncStatus>> = _statuses

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    /** All libraries together: syncing while any is, offline only when every one is. */
    val status: StateFlow<SyncStatus> = _status

    /** Books downloading right now, by id, with progress 0..1. */
    private val _downloads = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloads: StateFlow<Map<Long, Float>> = _downloads

    /** Bytes used by downloaded PC books. */
    val storageUsed: Flow<Long> = dao.observeServerBooks().map { books -> books.filter { it.localPath != null }.sumOf { it.sizeBytes } }

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short messages for the library's snackbar. */
    val notices: SharedFlow<String> = _notices

    // Pairing

    /** Pairs with the library whose QR code was scanned. */
    suspend fun pair(invite: PairingInvite): Result<String> = runCatching {
        connect(invite.addresses, invite.port, invite.certSha256, invite.code)
    }

    /**
     * Pairs by typed address and code. [certSha256] is what [LibraryClient.fingerprintAt]
     * reported and the person confirmed against the computer's screen.
     */
    suspend fun pairManually(address: String, port: Int, certSha256: String, code: String): Result<String> = runCatching {
        connect(listOf(address), port, certSha256, code)
    }

    /** Adds a library; one paired before is kept, and pairing it again just refreshes it. */
    private suspend fun connect(addresses: List<String>, port: Int, certSha256: String, code: String): String {
        settings.migrate()
        val request = PairRequest(code = code.filter(Char::isDigit), deviceId = settings.deviceId(), deviceName = deviceName())
        val (address, response) = LibraryClient.pair(addresses, port, certSha256, request)
        val before = settings.connection(response.serverId)
        settings.saveConnection(
            Connection(
                serverId = response.serverId,
                serverName = response.serverName,
                port = port,
                certSha256 = certSha256.lowercase(),
                addresses = (addresses + before?.addresses.orEmpty()).distinct(),
                token = response.token,
                lastGood = address,
                keepAll = before?.keepAll ?: false,
            ),
        )
        return response.serverName
    }

    /** Forgets one library: its place in shelves, its folders and moves waiting for it. Nothing on it changes. */
    suspend fun unpair(serverId: String) = lock.withLock { forgetLibrary(serverId) }

    private suspend fun forgetLibrary(serverId: String) {
        settings.removeConnection(serverId)
        dao.removeMemberships(serverId)
        dao.removeFolders(serverId)
        dao.clearPendingFor(serverId)
        forgetOrphans()
        _statuses.update { it - serverId }
        _status.value = if (settings.connectionsNow().isEmpty()) SyncStatus.NotPaired else overall()
    }

    // Catalogs

    /** Brings each library's catalog over, one after another. Quietly does nothing when not paired. */
    suspend fun sync(): Result<Unit> = lock.withLock {
        settings.migrate()
        val connections = settings.connectionsNow()
        if (connections.isEmpty()) {
            _status.value = SyncStatus.NotPaired
            return Result.success(Unit)
        }
        adoptFromSingleLibrary(connections.first())
        _status.value = SyncStatus.Syncing
        var reached: LibraryClient? = null
        for (connection in connections) {
            _statuses.update { it + (connection.serverId to SyncStatus.Syncing) }
            val result = runCatching { withContext(Dispatchers.IO) { syncOne(connection) } }
            val status = when (val error = result.exceptionOrNull()) {
                null -> {
                    reached = reached ?: client(connection)
                    SyncStatus.Idle
                }
                is NotPairedException -> {
                    // Removed on that computer: stop trying with a dead token.
                    forgetLibrary(connection.serverId)
                    _notices.tryEmit("${connection.serverName} no longer recognises this phone. Pair again to see its books.")
                    null
                }
                else -> SyncStatus.Offline(error.friendlyMessage())
            }
            status?.let { s -> _statuses.update { it + (connection.serverId to s) } }
        }
        // Books only this phone has are looked up by whichever library answered.
        reached?.let { client -> runCatching { withContext(Dispatchers.IO) { lookUpLocalBooks(client) } } }
        _status.value = overall()
        if (reached != null || settings.connectionsNow().isEmpty()) Result.success(Unit)
        else Result.failure(IOException((_status.value as? SyncStatus.Offline)?.reason ?: "Can't reach your libraries."))
    }

    private fun overall(): SyncStatus {
        val all = _statuses.value.values
        return when {
            all.isEmpty() -> SyncStatus.Idle
            all.any { it == SyncStatus.Syncing } -> SyncStatus.Syncing
            all.all { it is SyncStatus.Offline } -> all.first()
            else -> SyncStatus.Idle
        }
    }

    private suspend fun syncOne(connection: Connection) {
        val client = client(connection)
        val id = connection.serverId
        // The phone's own moves go first, so the catalog that follows already has them.
        val overtaken = pushChanges(client, id)
        // A change that library had something newer for: read everything again to settle on its answer.
        val page = client.catalog(if (overtaken > 0) 0 else settings.catalogCursor(id))
        page.books.forEach { upsert(client, id, it) }
        page.removed.forEach { removedId -> dao.serverBook(removedId)?.let { dao.removeMembership(id, it.id) } }
        forgetOrphans()
        page.folders.forEach { dao.saveFolder(FolderEntity(id, it.path, it.removed, it.stamp.at)) }
        settings.setCatalogCursor(id, page.cursor)
        settings.setLastSync(id, System.currentTimeMillis())
        if (overtaken > 0) {
            _notices.tryEmit(
                if (overtaken == 1) "A move was overtaken by a newer change on ${connection.serverName}."
                else "$overtaken moves were overtaken by newer changes on ${connection.serverName}.",
            )
        }
    }

    /**
     * A phone that had one library before memberships existed: its PC books all came from
     * that library, in the folders they show. Done once, when nothing is a member yet.
     */
    private suspend fun adoptFromSingleLibrary(first: Connection) {
        val books = dao.serverBooks()
        if (books.isEmpty() || dao.observeMemberships().first().isNotEmpty()) return
        books.forEach { dao.saveMembership(LibraryBookEntity(first.serverId, it.id, it.shelf)) }
    }

    private suspend fun upsert(client: LibraryClient, serverId: String, book: CatalogBook) {
        // The phone has this book itself (its folder): show that one, and know a library has it too.
        val own = dao.localBook(book.contentId) ?: book.previousIds.firstNotNullOfOrNull { dao.localBook(it) }
        if (own != null && own.source == BookSource.FOLDER) {
            if (!own.onPc) dao.setOnPc(own.id)
            dao.serverBook(book.contentId)?.let { dao.removeMembership(serverId, it.id) }
            return
        }
        // The library wrote information into the file, which gave it a new id: same book.
        val existing = dao.serverBook(book.contentId)
            ?: book.previousIds.firstNotNullOfOrNull { dao.serverBook(it) }
        val coversChanged = existing == null || existing.coverEtag != book.coverEtag
        val covers = if (coversChanged) fetchCovers(client, book) else null
        if (coversChanged && existing != null) deleteCovers(existing)

        val bookId = if (existing == null) {
            dao.insert(
                BookEntity(
                    uri = "$URI_SCHEME${book.contentId}",
                    source = BookSource.SERVER,
                    format = if (book.format == Format.PDF) BookFormat.PDF else BookFormat.EPUB,
                    title = book.title,
                    author = book.author,
                    sizeBytes = book.sizeBytes,
                    lastModified = book.addedAt,
                    coverThumbPath = covers?.thumb,
                    coverFullPath = covers?.full,
                    coverColor = covers?.color,
                    rightToLeft = book.rightToLeft,
                    addedAt = book.addedAt,
                    contentId = book.contentId,
                    coverEtag = book.coverEtag,
                    shelf = book.folder,
                    genres = book.genres.joinToString(GENRE_SEPARATOR),
                    description = book.description,
                    year = book.year,
                    series = book.series,
                    seriesIndex = book.seriesIndex,
                ),
            )
        } else {
            dao.update(
                existing.copy(
                    // A re-keyed book keeps its download: the old copy reads the same, only
                    // its information was behind.
                    contentId = book.contentId,
                    uri = "$URI_SCHEME${book.contentId}",
                    sizeBytes = if (existing.localPath == null) book.sizeBytes else existing.sizeBytes,
                    description = book.description,
                    year = book.year,
                    series = book.series,
                    seriesIndex = book.seriesIndex,
                    title = book.title,
                    author = book.author,
                    rightToLeft = book.rightToLeft,
                    coverThumbPath = if (coversChanged) covers?.thumb else existing.coverThumbPath,
                    coverFullPath = if (coversChanged) covers?.full else existing.coverFullPath,
                    coverColor = if (coversChanged) covers?.color else existing.coverColor,
                    coverEtag = book.coverEtag,
                    genres = book.genres.joinToString(GENRE_SEPARATOR),
                ),
            )
            existing.id
        }
        // Its folder in this library, unless it was moved here and that library hasn't confirmed it yet.
        val membership = dao.memberships(bookId).firstOrNull { it.serverId == serverId }
        if (membership?.folderDirty != true) dao.saveMembership(LibraryBookEntity(serverId, bookId, book.folder))
    }

    private class Covers(val thumb: String?, val full: String?, val color: Int?)

    private suspend fun fetchCovers(client: LibraryClient, book: CatalogBook): Covers? {
        val etag = book.coverEtag ?: return null
        // The etag is in the name, so a changed cover is a new file and never a stale cache hit.
        val thumb = File(coverDir, "srv_${book.contentId}_${etag}_thumb.jpg")
        val full = File(coverDir, "srv_${book.contentId}_${etag}_full.jpg")
        val gotThumb = runCatching { client.cover(book.contentId, full = false, target = thumb) }.getOrDefault(false)
        val gotFull = runCatching { client.cover(book.contentId, full = true, target = full) }.getOrDefault(false)
        val color = if (gotThumb) {
            BitmapFactory.decodeFile(thumb.path)?.let { Palette.from(it).generate().getDominantColor(Color.DKGRAY) }
        } else {
            null
        }
        return Covers(thumb.path.takeIf { gotThumb }, full.path.takeIf { gotFull }, color)
    }

    /**
     * Sends organizing changes waiting for library [serverId]. Returns how many lost to
     * something newer there. Books moved meanwhile stay marked, so the next sync carries them.
     */
    private suspend fun pushChanges(client: LibraryClient, serverId: String): Int {
        val pending = dao.pending(serverId)
        if (pending.isEmpty()) return 0
        val dirty = dao.dirtyMemberships(serverId)
        val changes = pending.mapNotNull { runCatching { Protocol.json.decodeFromString(OrganizeChange.serializer(), it.json) }.getOrNull() }
        val result = client.organize(changes)
        dao.clearPending(pending.map { it.id })
        dao.clearMembershipDirty(serverId, dirty)
        return result.lost.size
    }

    /**
     * Asks a library to look up books only this phone has (its own folder and imports), a
     * few at a time, and fills in what they lack. Quietly stops if it can't look up.
     */
    private suspend fun lookUpLocalBooks(client: LibraryClient) {
        while (true) {
            val batch = dao.localBooksAwaitingLookup(LOOKUP_BATCH)
            if (batch.isEmpty()) return
            val results = try {
                client.lookup(batch.map { LookupQuery(it.id.toString(), it.title, it.author, it.titleFromFile) })
            } catch (e: IOException) {
                return // Lookups are off there, or it's out of reach: try again next sync.
            }.associateBy { it.key }
            for (book in batch) {
                val result = results[book.id.toString()]
                if (result == null || !result.found) {
                    dao.update(book.copy(lookedUp = true))
                    continue
                }
                val cover = if (book.coverThumbPath == null) {
                    result.coverId
                        ?.let { runCatching { client.lookupCover(it) }.getOrNull() }
                        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?.let { covers.saveCover(book.uri, it) }
                } else {
                    null
                }
                dao.update(
                    book.copy(
                        title = if (book.titleFromFile) result.title ?: book.title else book.title,
                        titleFromFile = book.titleFromFile && result.title == null,
                        author = book.author ?: result.author,
                        genres = book.genres.ifEmpty { result.genres.joinToString(GENRE_SEPARATOR) },
                        description = book.description ?: result.description,
                        year = book.year ?: result.year,
                        coverThumbPath = cover?.thumbPath ?: book.coverThumbPath,
                        coverFullPath = cover?.fullPath ?: book.coverFullPath,
                        coverColor = cover?.color ?: book.coverColor,
                        lookedUp = true,
                    ),
                )
            }
        }
    }

    // Downloads

    /** The libraries that have [book], the ones that answered last time first. */
    private suspend fun librariesWith(book: BookEntity): List<Connection> {
        val ids = dao.memberships(book.id).map { it.serverId }.toSet()
        return settings.connectionsNow()
            .filter { it.serverId in ids }
            .sortedBy { if (_statuses.value[it.serverId] is SyncStatus.Offline) 1 else 0 }
    }

    /**
     * Downloads a PC book so it can be opened, from whichever library that has it answers,
     * checking every byte against its content id. Returns the book with its local copy.
     */
    suspend fun download(book: BookEntity): Result<BookEntity> = runCatching {
        if (book.downloaded) return@runCatching book
        val libraries = librariesWith(book).ifEmpty { throw IOException("Pair with a library that has this book to download it.") }
        val contentId = book.contentId!!
        val extension = if (book.format == BookFormat.PDF) "pdf" else "epub"
        val target = File(bookDir, "$contentId.$extension")
        val part = File(bookDir, "$contentId.$extension.part")

        _downloads.update { it + (book.id to 0f) }
        try {
            withContext(Dispatchers.IO) {
                var last: Throwable? = null
                for (connection in libraries) {
                    val fetched = runCatching {
                        client(connection).download(contentId, part) { bytes ->
                            val progress = if (book.sizeBytes > 0) (bytes.toFloat() / book.sizeBytes).coerceIn(0f, 1f) else 0f
                            _downloads.update { it + (book.id to progress) }
                        }
                    }
                    last = fetched.exceptionOrNull()
                    if (last == null) break
                }
                last?.let { throw it }
                val actual = part.inputStream().use(::contentIdOf)
                if (actual != contentId) {
                    part.delete()
                    throw IOException("The download was damaged. Try again.")
                }
                if (!part.renameTo(target)) throw IOException("Couldn't save the book.")
            }
            dao.setLocalPath(book.id, target.absolutePath)
            freeUpSpace(keep = book.id)
            dao.get(book.id)!!
        } finally {
            _downloads.update { it - book.id }
        }
    }.recoverCatching { throw IOException(it.friendlyMessage(), it) }

    suspend fun removeDownload(book: BookEntity) {
        book.localPath?.let { File(it).delete() }
        dao.setLocalPath(book.id, null)
        dao.setPinned(book.id, false)
    }

    suspend fun setPinned(book: BookEntity, pinned: Boolean) = dao.setPinned(book.id, pinned)

    /** Books in a library on full sync: never cleared to make room. */
    private suspend fun keptByFullSync(): Set<Long> {
        val full = settings.connectionsNow().filter { it.keepAll }.map { it.serverId }
        return full.flatMap { dao.membershipsIn(it) }.map { it.bookId }.toSet()
    }

    /**
     * Clears downloads, least recently read first, until they fit in the storage limit.
     * Pinned books, books from a library on full sync and [keep] (the book being opened)
     * are never cleared. With [everything], clears every one of the others regardless.
     */
    suspend fun freeUpSpace(keep: Long? = null, everything: Boolean = false) {
        val limit = settings.storageLimit.first()
        if (limit <= 0 && !everything) return
        val kept = keptByFullSync()
        val downloaded = dao.serverBooks().filter { it.localPath != null }
        var used = downloaded.sumOf { it.sizeBytes }
        val candidates = downloaded
            .filter { !it.pinned && it.id != keep && it.id !in kept }
            .sortedBy { it.lastOpenedAt ?: 0L }
        for (book in candidates) {
            if (!everything && used <= limit) break
            removeDownload(book)
            used -= book.sizeBytes
        }
    }

    // Full sync: every book of a chosen library on the phone, every phone book in a library

    /** How keeping every book is going for one library. */
    data class KeepAll(val onPhone: Int, val total: Int, val outOfRoom: Boolean)

    private val _keepAll = MutableStateFlow<Map<String, KeepAll>>(emptyMap())

    /** Progress of full sync, by library id. */
    val keepAllProgress: StateFlow<Map<String, KeepAll>> = _keepAll

    /**
     * Downloads the books of every library on full sync that aren't on the phone yet,
     * newest first, while they fit in the storage limit. Stops at the limit rather than
     * clearing anything.
     */
    suspend fun downloadAll() {
        val full = settings.connectionsNow().filter { it.keepAll }
        _keepAll.update { it.filterKeys { id -> full.any { c -> c.serverId == id } } }
        if (full.isEmpty()) return
        val limit = settings.storageLimit.first()
        var used = storageUsed.first()
        val wanted = full.flatMap { dao.membershipsIn(it.serverId) }.map { it.bookId }.toSet()
        var outOfRoom = false
        for (book in dao.serverBooksToDownload().filter { it.id in wanted }) {
            if (limit > 0 && used + book.sizeBytes > limit) {
                outOfRoom = true
                continue
            }
            if (download(book).isSuccess) used += book.sizeBytes else break
            reportKeepAll(full, outOfRoom)
        }
        reportKeepAll(full, outOfRoom)
    }

    private suspend fun reportKeepAll(full: List<Connection>, outOfRoom: Boolean) {
        val books = dao.serverBooks().associateBy { it.id }
        _keepAll.value = full.associate { c ->
            val mine = dao.membershipsIn(c.serverId).mapNotNull { books[it.bookId] }
            c.serverId to KeepAll(mine.count { it.localPath != null }, mine.size, outOfRoom)
        }
    }

    /**
     * Works out ids for phone books (folder and imports), a few at a time, so a library can
     * tell which it already has. Only books changed since last time are read again.
     */
    suspend fun hashLocalBooks(limit: Int = HASH_BATCH) = withContext(Dispatchers.IO) {
        for (book in dao.localBooksToHash(limit)) {
            val id = runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(book.uri))!!.use(::contentIdOf)
            }.getOrNull() ?: continue
            dao.setHash(book.id, id, book.sizeBytes, book.lastModified)
        }
    }

    /**
     * Sends phone books the chosen library doesn't have into it, each into the folder it's
     * on here. An imported book then becomes that library's book with its file kept, so it
     * shows once and its moves reach the library; a folder book stays read from its folder.
     */
    suspend fun uploadLocalBooks(limit: Int = UPLOAD_BATCH): Int = withContext(Dispatchers.IO) {
        if (!settings.shareLocal.first()) return@withContext 0
        val target = settings.uploadTo.first()?.let { settings.connection(it) } ?: return@withContext 0
        val client = client(target)
        var sent = 0
        for (book in dao.localBooksToUpload(limit)) {
            val id = book.contentId!!
            val already = dao.serverBook(id)
            val catalog = if (already != null) null else {
                val extension = if (book.format == BookFormat.PDF) "pdf" else "epub"
                val name = listOfNotNull(book.author, book.title).joinToString(" - ") + ".$extension"
                val (file, temporary) = localFile(book) ?: continue
                try {
                    client.upload(file, name, book.shelf.ifEmpty { null })
                } catch (e: IOException) {
                    if (e is NotPairedException) throw e
                    break // Out of reach: the rest wait for next time.
                } finally {
                    if (temporary) file.delete()
                }
            }
            if (book.source == BookSource.IMPORTED) adopt(book, already, catalog, target.serverId) else dao.setOnPc(book.id)
            sent++
        }
        sent
    }

    /** An imported book a library now has: becomes that library's book, with the import as its download. */
    private suspend fun adopt(book: BookEntity, already: BookEntity?, catalog: CatalogBook?, serverId: String) {
        val path = android.net.Uri.parse(book.uri).path ?: return
        if (already != null) {
            // A library had it all along: keep that row, with this copy as its download and this reading.
            val newer = (book.lastOpenedAt ?: 0) > (already.lastOpenedAt ?: 0)
            dao.update(
                already.copy(
                    localPath = already.localPath ?: path,
                    pinned = true,
                    locatorJson = if (newer) book.locatorJson else already.locatorJson,
                    progression = if (newer) book.progression else already.progression,
                    lastOpenedAt = maxOf(book.lastOpenedAt ?: 0, already.lastOpenedAt ?: 0).takeIf { it > 0 },
                ),
            )
            if (already.localPath != null) File(path).delete()
            dao.delete(listOf(book.id))
            return
        }
        val c = catalog ?: return
        dao.update(
            book.copy(
                source = BookSource.SERVER,
                uri = "$URI_SCHEME${c.contentId}",
                contentId = c.contentId,
                localPath = path,
                // The person's own book: never cleared to make room.
                pinned = true,
                shelf = c.folder,
                onPc = true,
            ),
        )
        dao.saveMembership(LibraryBookEntity(serverId, book.id, c.folder))
    }

    /** A readable file for a phone book: the import itself, or a copy of a folder book. */
    private fun localFile(book: BookEntity): Pair<File, Boolean>? = runCatching {
        val uri = android.net.Uri.parse(book.uri)
        if (uri.scheme == "file") return@runCatching File(uri.path!!) to false
        val copy = File(context.cacheDir, "upload-${book.id}.part")
        context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
        copy to true
    }.getOrNull()

    // Housekeeping

    /** PC books no paired library has any more: gone from the phone, download and all. */
    private suspend fun forgetOrphans() = dao.orphanServerBooks().forEach { forget(it) }

    private suspend fun forget(book: BookEntity) {
        book.localPath?.let { File(it).delete() }
        deleteCovers(book)
        dao.delete(listOf(book.id))
    }

    private fun deleteCovers(book: BookEntity) {
        listOfNotNull(book.coverThumbPath, book.coverFullPath).forEach { File(it).delete() }
    }

    private fun client(connection: Connection) = LibraryClient(connection) { settings.rememberGoodAddress(connection.serverId, it) }

    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}"

    companion object {
        /** Placeholder uri for PC books: unique per book, never opened directly. */
        const val URI_SCHEME = "perform-server:"

        /** A library looks up one book a second, so a small batch answers well within the timeout. */
        private const val LOOKUP_BATCH = 8

        /** Hashing reads whole files: a few per pass keeps the phone cool. */
        private const val HASH_BATCH = 12
        private const val UPLOAD_BATCH = 20
    }
}

/** A sentence for the snackbar instead of a socket error. */
fun Throwable.friendlyMessage(): String = when (this) {
    is NotPairedException, is ServerException -> message ?: "The computer turned that down."
    is javax.net.ssl.SSLException -> "Something other than your computer answered. Pair again from Perform Library."
    is java.net.SocketTimeoutException, is java.net.ConnectException, is java.net.UnknownHostException, is java.net.NoRouteToHostException ->
        "Can't reach it. Check it's on and Perform Library is running, or turn on Tailscale."
    is IOException -> message ?: "Can't reach your computer."
    else -> message ?: "Something went wrong."
}
