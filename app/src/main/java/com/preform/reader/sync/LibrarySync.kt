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
import com.preform.reader.library.GENRE_SEPARATOR
import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.PairRequest
import com.preform.sync.PairingInvite
import com.preform.sync.contentIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
                val page = client.catalog(settings.catalogCursor())
                page.books.forEach { upsert(client, it) }
                page.removed.forEach { id -> dao.serverBook(id)?.let { forget(it) } }
                settings.setCatalogCursor(page.cursor)
                settings.setLastSync(System.currentTimeMillis())
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
        val existing = dao.serverBook(book.contentId)
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
                ),
            )
        } else {
            dao.update(
                existing.copy(
                    title = book.title,
                    author = book.author,
                    rightToLeft = book.rightToLeft,
                    coverThumbPath = if (coversChanged) covers?.thumb else existing.coverThumbPath,
                    coverFullPath = if (coversChanged) covers?.full else existing.coverFullPath,
                    coverColor = if (coversChanged) covers?.color else existing.coverColor,
                    coverEtag = book.coverEtag,
                    shelf = book.folder,
                    genres = book.genres.joinToString(GENRE_SEPARATOR),
                ),
            )
        }
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

    // Housekeeping

    private suspend fun forgetServerBooks() = dao.serverBooks().forEach { forget(it) }

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
