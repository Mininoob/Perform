package com.preform.reader.sync

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.palette.graphics.Palette
import com.preform.reader.library.BookDao
import com.preform.reader.library.BookEntity
import com.preform.reader.library.BookFormat
import com.preform.reader.library.BookSource
import com.preform.reader.library.CoverExtractor
import com.preform.reader.library.FolderEntity
import com.preform.sync.LookupQuery
import com.preform.reader.library.GENRE_SEPARATOR
import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.PairRequest
import com.preform.sync.OrganizeChange
import com.preform.sync.PairingInvite
import com.preform.sync.Protocol
import com.preform.sync.contentIdOf
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

    /** The last sync didn't reach the PC; books already downloaded still open. */
    data class Offline(val reason: String) : SyncStatus
}

/**
 * Keeps the phone's copy of the PC library current: the catalog with covers, books
 * downloaded when they're first opened, and the space those downloads take.
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

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    /** Books downloading right now, by id, with progress 0..1. */
    private val _downloads = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloads: StateFlow<Map<Long, Float>> = _downloads

    /** Bytes used by downloaded PC books. */
    val storageUsed: Flow<Long> = dao.observeServerBooks().map { books -> books.filter { it.localPath != null }.sumOf { it.sizeBytes } }

    // Pairing

    /** Pairs with the PC whose QR code was scanned. */
    suspend fun pair(invite: PairingInvite): Result<String> = runCatching {
        connect(invite.addresses, invite.port, invite.certSha256, invite.code)
    }

    /**
     * Pairs by typed address and code. [certSha256] is what [LibraryClient.fingerprintAt]
     * reported and the person confirmed against the PC's screen.
     */
    suspend fun pairManually(address: String, port: Int, certSha256: String, code: String): Result<String> = runCatching {
        connect(listOf(address), port, certSha256, code)
    }

    private suspend fun connect(addresses: List<String>, port: Int, certSha256: String, code: String): String {
        val request = PairRequest(code = code.filter(Char::isDigit), deviceId = settings.deviceId(), deviceName = deviceName())
        val (address, response) = LibraryClient.pair(addresses, port, certSha256, request)
        val previous = settings.connectionNow()
        if (previous != null && previous.serverId != response.serverId) forgetServerBooks()
        settings.setConnection(
            Connection(
                serverId = response.serverId,
                serverName = response.serverName,
                port = port,
                certSha256 = certSha256.lowercase(),
                addresses = addresses,
                token = response.token,
                lastGood = address,
            ),
        )
        return response.serverName
    }

    /** Forgets the PC, its books and their downloads. Nothing on the PC changes. */
    suspend fun unpair() = lock.withLock {
        settings.setConnection(null)
        forgetServerBooks()
        _status.value = SyncStatus.NotPaired
    }

    // Catalog

    /** Brings the PC's catalog over. Quietly does nothing when not paired. */
    suspend fun sync(): Result<Unit> = lock.withLock {
        val connection = settings.connectionNow()
        if (connection == null) {
            _status.value = SyncStatus.NotPaired
            return Result.success(Unit)
        }
        _status.value = SyncStatus.Syncing
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val client = client(connection)
                // The phone's own moves go first, so the catalog that follows already has them.
                val overtaken = pushChanges(client)
                // A change the PC had something newer for: read everything again to settle on its answer.
                val page = client.catalog(if (overtaken > 0) 0 else settings.catalogCursor())
                page.books.forEach { upsert(client, it) }
                page.removed.forEach { id -> dao.serverBook(id)?.let { forget(it) } }
                page.folders.forEach { dao.saveFolder(FolderEntity(it.path, it.removed, it.stamp.at)) }
                if (overtaken > 0) {
                    _notices.tryEmit(if (overtaken == 1) "A move was overtaken by a newer change on your PC." else "$overtaken moves were overtaken by newer changes on your PC.")
                }
                settings.setCatalogCursor(page.cursor)
                settings.setLastSync(System.currentTimeMillis())
                lookUpLocalBooks(client)
                Unit
            }
        }
        _status.value = when (val error = result.exceptionOrNull()) {
            null -> SyncStatus.Idle
            is NotPairedException -> {
                // Removed in the tray app: stop trying with a dead token.
                settings.setConnection(null)
                forgetServerBooks()
                SyncStatus.NotPaired
            }
            else -> SyncStatus.Offline(error.friendlyMessage())
        }
        result
    }

    private suspend fun upsert(client: LibraryClient, book: CatalogBook) {
        // The phone has this book itself (its folder): show that one, and know the PC has it too.
        val own = dao.localBook(book.contentId) ?: book.previousIds.firstNotNullOfOrNull { dao.localBook(it) }
        if (own != null && own.source == BookSource.FOLDER) {
            if (!own.onPc) dao.setOnPc(own.id)
            dao.serverBook(book.contentId)?.let { forget(it) }
            return
        }
        // The PC wrote information into the file, which gave it a new id: same book.
        val existing = dao.serverBook(book.contentId)
            ?: book.previousIds.firstNotNullOfOrNull { dao.serverBook(it) }
        val coversChanged = existing == null || existing.coverEtag != book.coverEtag
        val covers = if (coversChanged) fetchCovers(client, book) else null
        if (coversChanged && existing != null) deleteCovers(existing)

        if (existing == null) {
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
                    // Moved here and not confirmed yet: keep the phone's shelf until the PC has it.
                    shelf = if (existing.shelfDirty) existing.shelf else book.folder,
                    genres = book.genres.joinToString(GENRE_SEPARATOR),
                ),
            )
        }
    }

    /**
     * Sends organizing changes waiting on the phone. Returns how many lost to something
     * newer on the PC. Books moved meanwhile stay marked, so the next sync carries them.
     */
    private suspend fun pushChanges(client: LibraryClient): Int {
        val pending = dao.pending()
        if (pending.isEmpty()) return 0
        val dirty = dao.dirtyShelves()
        val changes = pending.mapNotNull { runCatching { Protocol.json.decodeFromString(OrganizeChange.serializer(), it.json) }.getOrNull() }
        val result = client.organize(changes)
        dao.clearPending(pending.map { it.id })
        dao.clearShelfDirty(dirty)
        return result.lost.size
    }

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short messages for the library's snackbar. */
    val notices: SharedFlow<String> = _notices

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
     * Asks the PC to look up books only this phone has (its own folder and imports), a
     * few at a time, and fills in what they lack. Quietly stops if the PC can't look up.
     */
    private suspend fun lookUpLocalBooks(client: LibraryClient) {
        while (true) {
            val batch = dao.localBooksAwaitingLookup(LOOKUP_BATCH)
            if (batch.isEmpty()) return
            val results = try {
                client.lookup(batch.map { LookupQuery(it.id.toString(), it.title, it.author, it.titleFromFile) })
            } catch (e: IOException) {
                return // Lookups are off on the PC, or it's out of reach: try again next sync.
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

    /**
     * Downloads a PC book so it can be opened, checking every byte against its content
     * id. Returns the book with its local copy.
     */
    suspend fun download(book: BookEntity): Result<BookEntity> = runCatching {
        if (book.downloaded) return@runCatching book
        val connection = settings.connectionNow() ?: throw IOException("Pair with your PC to download this book.")
        val contentId = book.contentId!!
        val extension = if (book.format == BookFormat.PDF) "pdf" else "epub"
        val target = File(bookDir, "$contentId.$extension")
        val part = File(bookDir, "$contentId.$extension.part")

        _downloads.update { it + (book.id to 0f) }
        try {
            withContext(Dispatchers.IO) {
                client(connection).download(contentId, part) { bytes ->
                    val progress = if (book.sizeBytes > 0) (bytes.toFloat() / book.sizeBytes).coerceIn(0f, 1f) else 0f
                    _downloads.update { it + (book.id to progress) }
                }
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

    /**
     * Clears downloads, least recently read first, until they fit in the storage limit.
     * Pinned books and [keep] (the book being opened) are never cleared. With
     * [everything], clears every unpinned download regardless of the limit.
     */
    suspend fun freeUpSpace(keep: Long? = null, everything: Boolean = false) {
        val limit = settings.storageLimit.first()
        if (limit <= 0 && !everything) return
        // Keeping every book: nothing is cleared behind the person's back.
        if (!everything && settings.keepAll.first()) return
        val downloaded = dao.serverBooks().filter { it.localPath != null }
        var used = downloaded.sumOf { it.sizeBytes }
        val candidates = downloaded
            .filter { !it.pinned && it.id != keep }
            .sortedBy { it.lastOpenedAt ?: 0L }
        for (book in candidates) {
            if (!everything && used <= limit) break
            removeDownload(book)
            used -= book.sizeBytes
        }
    }

    // Full sync: every PC book on the phone, every phone book on the PC

    /** How keeping every book is going, for the Private library screen. */
    data class KeepAll(val onPhone: Int, val total: Int, val outOfRoom: Boolean)

    private val _keepAll = MutableStateFlow<KeepAll?>(null)
    val keepAllProgress: StateFlow<KeepAll?> = _keepAll

    /**
     * Downloads PC books not on the phone yet, newest first, while they fit in the storage
     * limit. Stops at the limit rather than clearing anything.
     */
    suspend fun downloadAll() {
        if (!settings.keepAll.first() || settings.connectionNow() == null) return
        val limit = settings.storageLimit.first()
        var used = storageUsed.first()
        var outOfRoom = false
        for (book in dao.serverBooksToDownload()) {
            if (limit > 0 && used + book.sizeBytes > limit) {
                outOfRoom = true
                continue
            }
            if (download(book).isSuccess) used += book.sizeBytes else break
            reportKeepAll(outOfRoom)
        }
        reportKeepAll(outOfRoom)
    }

    private suspend fun reportKeepAll(outOfRoom: Boolean) {
        val all = dao.serverBooks()
        _keepAll.value = KeepAll(all.count { it.localPath != null }, all.size, outOfRoom)
    }

    /**
     * Works out ids for phone books (folder and imports), a few at a time, so the PC can
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
     * Sends phone books the PC doesn't have into its library, each into the folder it's
     * on here. An imported book then becomes a PC book that keeps its file, so it shows
     * once and its moves reach the PC; a folder book stays read from its folder.
     */
    suspend fun uploadLocalBooks(limit: Int = UPLOAD_BATCH): Int = withContext(Dispatchers.IO) {
        if (!settings.shareLocal.first()) return@withContext 0
        val connection = settings.connectionNow() ?: return@withContext 0
        val client = client(connection)
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
            if (book.source == BookSource.IMPORTED) adopt(book, already, catalog) else dao.setOnPc(book.id)
            sent++
        }
        sent
    }

    /** An imported book the PC now has: becomes the PC book, with the import as its download. */
    private suspend fun adopt(book: BookEntity, already: BookEntity?, catalog: CatalogBook?) {
        val path = android.net.Uri.parse(book.uri).path ?: return
        if (already != null) {
            // The PC had it all along: keep the PC's row, with this copy as its download and this reading.
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

    private suspend fun forgetServerBooks() {
        dao.serverBooks().forEach { forget(it) }
        // Moves meant for that PC mean nothing to another one.
        dao.clearAllPending()
    }

    private suspend fun forget(book: BookEntity) {
        book.localPath?.let { File(it).delete() }
        deleteCovers(book)
        dao.delete(listOf(book.id))
    }

    private fun deleteCovers(book: BookEntity) {
        listOfNotNull(book.coverThumbPath, book.coverFullPath).forEach { File(it).delete() }
    }

    private fun client(connection: Connection) = LibraryClient(connection) { settings.rememberGoodAddress(it) }

    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}"

    companion object {
        /** Placeholder uri for PC books: unique per book, never opened directly. */
        const val URI_SCHEME = "preform-server:"

        /** The PC looks up one book a second, so a small batch answers well within the timeout. */
        private const val LOOKUP_BATCH = 8

        /** Hashing reads whole files: a few per pass keeps the phone cool. */
        private const val HASH_BATCH = 12
        private const val UPLOAD_BATCH = 20
    }
}

/** A sentence for the snackbar instead of a socket error. */
fun Throwable.friendlyMessage(): String = when (this) {
    is NotPairedException, is ServerException -> message ?: "The PC turned that down."
    is javax.net.ssl.SSLException -> "Something other than your PC answered. Pair again from the PC app."
    is java.net.SocketTimeoutException, is java.net.ConnectException, is java.net.UnknownHostException, is java.net.NoRouteToHostException ->
        "Can't reach your PC. Check it's on and Preform Library is running, or turn on Tailscale."
    is IOException -> message ?: "Can't reach your PC."
    else -> message ?: "Something went wrong."
}
