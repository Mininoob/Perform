package com.preform.desktop

import com.preform.sync.PairRequest
import com.preform.sync.PeerHello
import com.preform.sync.PeerKind
import com.preform.sync.contentIdOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/** A peer as the window shows it: the record, whether it answered last time, and its book count. */
data class PeerView(val peer: Peer, val online: Boolean, val bookCount: Int)

/** What happened when a book was copied from another library. */
sealed interface CopyResult {
    data class Added(val result: Library.ImportResult) : CopyResult

    /** A phone's book: asked for, it arrives on the phone's next sync. */
    data object Requested : CopyResult
}

/**
 * This library's view of the others: pairing with another computer, keeping each peer's
 * catalog and thumbnails (so it stays browsable offline), and copying books over.
 * Nothing here ever changes the other library.
 */
class PeerService(
    private val peers: PeerStore,
    private val library: Library,
    private val covers: Path,
    private val temp: Path,
    private val ourId: () -> String,
    private val ourName: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val online = MutableStateFlow(emptySet<String>())

    private val _views = MutableStateFlow(views())
    val views: StateFlow<List<PeerView>> = _views

    fun refreshViews() {
        _views.value = views()
    }

    private fun views(): List<PeerView> {
        val counts = peers.bookCounts()
        return peers.peers().map { PeerView(it, it.peerId in online.value || it.kind == PeerKind.PHONE && it.lastSync != null, counts[it.peerId] ?: 0) }
    }

    // Connecting to another computer

    /** Who's at an address, and the certificate it shows (for the security check). */
    fun hello(address: String, port: Int): Pair<PeerHello, String> = PeerClient.hello(address, port)

    /**
     * Pairs with another computer's library using the code it shows under Pair, trusting
     * [certSha256] (which the person confirmed matches the other screen).
     */
    fun connect(addresses: List<String>, port: Int, certSha256: String, code: String, hello: PeerHello): Peer {
        val request = PairRequest(code = code.filter(Char::isDigit), deviceId = ourId(), deviceName = ourName(), kind = PeerKind.COMPUTER)
        val (address, response) = PeerClient.pair(addresses, port, certSha256, request)
        val peer = Peer(
            peerId = response.serverId,
            name = response.serverName,
            kind = PeerKind.COMPUTER,
            platform = hello.platform,
            addresses = addresses,
            port = port,
            certSha256 = certSha256.lowercase(),
            token = response.token,
            lastGood = address,
        )
        peers.savePeer(peer)
        refresh(peer.peerId)
        return peer
    }

    /** Forgets a peer and its catalog here. The other library is not changed. */
    fun disconnect(peerId: String) {
        peers.removePeer(peerId)
        covers.resolve("peers").resolve(peerId).toFile().deleteRecursively()
        online.update { it - peerId }
        refreshViews()
    }

    fun setWatched(peerId: String, watched: Boolean) {
        peers.peer(peerId)?.let { peers.savePeer(it.copy(watched = watched)) }
        refreshViews()
    }

    // Their catalogs

    /** Pulls catalog changes and new thumbnails from every computer peer (or one). */
    fun refresh(peerId: String? = null) {
        peers.peers()
            .filter { it.kind == PeerKind.COMPUTER && (peerId == null || it.peerId == peerId) }
            .forEach { peer ->
                val result = runCatching { pull(peer) }
                online.update { if (result.isSuccess) it + peer.peerId else it - peer.peerId }
            }
        refreshViews()
    }

    private fun pull(peer: Peer) {
        val client = client(peer)
        val page = client.catalog(peer.cursor)
        peers.applyCatalog(peer.peerId, page.books, page.removed, page.cursor, clock())
        // Thumbnails for books whose cover is new or changed; full covers load when looked at.
        page.books.filter { it.coverEtag != null }.forEach { book ->
            val target = coverPath(peer.peerId, book.contentId, book.coverEtag!!, full = false)
            if (!target.exists()) runCatching { client.cover(book.contentId, full = false, target = target) }
        }
    }

    fun books(peerId: String): List<LibraryBook> = peers.books(peerId)

    /** A peer book's cover on disk, fetching the full size on first look if it's a computer's. */
    fun cover(book: LibraryBook, full: Boolean): Path? {
        val peerId = book.peerId ?: return null
        val etag = book.catalog.coverEtag ?: return null
        val thumb = coverPath(peerId, book.catalog.contentId, etag, full = false)
        if (!full) return thumb.takeIf { it.exists() }
        val large = coverPath(peerId, book.catalog.contentId, etag, full = true)
        if (large.exists()) return large
        val peer = peers.peer(peerId)
        if (peer?.kind == PeerKind.COMPUTER && runCatching { client(peer).cover(book.catalog.contentId, full = true, target = large) }.getOrDefault(false)) {
            return large
        }
        return thumb.takeIf { it.exists() }
    }

    fun coverPath(peerId: String, contentId: String, etag: String, full: Boolean): Path =
        covers.resolve("peers").resolve(peerId).resolve("${contentId}_${etag}_${if (full) "full" else "thumb"}.jpg")

    // Copying a book over

    /**
     * Copies [book] from its library into [folder] of this one. From a computer, the file is
     * downloaded, checked byte for byte against its id, and imported; from a phone, a copy
     * is asked for and arrives on the phone's next sync.
     */
    fun copy(book: LibraryBook, folder: String, onProgress: (Long) -> Unit = {}): CopyResult {
        val peer = book.peerId?.let(peers::peer) ?: throw OrganizeException("That library is no longer connected.")
        val id = book.catalog.contentId
        if (peer.kind == PeerKind.PHONE) {
            peers.requestCopy(peer.peerId, id, folder, clock())
            refreshViews()
            return CopyResult.Requested
        }
        val part = temp.resolve("peer-$id.part")
        client(peer).download(id, part, onProgress)
        if (part.inputStream().use(::contentIdOf) != id) {
            Files.deleteIfExists(part)
            throw OrganizeException("The copy from ${peer.name} arrived damaged. Try again.")
        }
        // Name the file after the book, as the other library would.
        val named = temp.resolve("copy-$id").createDirectories()
            .resolve(Library.safeFileName(listOfNotNull(book.catalog.author, book.catalog.title).joinToString(" - "), book.catalog.format))
        Files.move(part, named, StandardCopyOption.REPLACE_EXISTING)
        try {
            return CopyResult.Added(library.import(listOf(named), folder))
        } finally {
            named.parent.toFile().deleteRecursively()
        }
    }

    fun pendingRequests() = peers.allRequests()

    private fun client(peer: Peer) = PeerClient(peer) { address ->
        if (peer.lastGood != address) peers.savePeer(peer.copy(lastGood = address))
    }
}

/** Raised for a peer that was unreachable on every address. */
class PeerOfflineException(name: String) : IOException("$name can't be reached right now.")
