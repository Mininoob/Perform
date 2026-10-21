package com.perform.desktop

import com.perform.sync.FolderStamp
import com.perform.sync.OrganizeChange
import com.perform.sync.PairRequest
import com.perform.sync.Stamp
import com.perform.sync.PeerHello
import com.perform.sync.PeerKind
import com.perform.sync.contentIdOf
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

/** A peer as the window shows it: the record, whether it answered last time, its book count, and whether it mirrors us. */
data class PeerView(val peer: Peer, val online: Boolean, val bookCount: Int, val mirrorsUs: Boolean = false)

/** What turning mirroring on would do here first: books to copy (and their size), books to move to match. */
data class MirrorPreview(val copy: Int, val bytes: Long, val move: Int)

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
    /** For mirroring: the store, to match books and keep the other library's stamps. */
    private val store: Store? = null,
    /** Where removals followed from a mirror go: the Recycle Bin / Trash, never deleted. */
    private val trash: (Path) -> Boolean = Library::moveToTrash,
    /** What mirroring is doing, for the status line; null when done. */
    private val onActivity: (String?) -> Unit = {},
) {
    private val online = MutableStateFlow(emptySet<String>())
    private val theyMirror = MutableStateFlow(emptySet<String>())

    private val _views = MutableStateFlow(views())
    val views: StateFlow<List<PeerView>> = _views

    fun refreshViews() {
        _views.value = views()
    }

    private fun views(): List<PeerView> {
        val counts = peers.bookCounts()
        return peers.peers().map {
            PeerView(it, it.peerId in online.value || it.kind == PeerKind.PHONE && it.lastSync != null, counts[it.peerId] ?: 0, it.peerId in theyMirror.value)
        }
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
        // Whether it mirrors us, for the Devices page; never worth failing a pull over.
        runCatching { client.hello() }.onSuccess { hello ->
            theyMirror.update { if (ourId() in hello.mirroring) it + peer.peerId else it - peer.peerId }
        }
        val page = client.catalog(peer.cursor)
        if (peer.mirror) {
            // Removals first, while the link from their book to ours is still known.
            followRemovals(peer, page.removedStamps)
        }
        peers.applyCatalog(peer.peerId, page.books, page.removed, page.cursor, clock())
        // Thumbnails for books whose cover is new or changed; full covers load when looked at.
        page.books.filter { it.coverEtag != null }.forEach { book ->
            val target = coverPath(peer.peerId, book.contentId, book.coverEtag!!, full = false)
            if (!target.exists()) runCatching { client.cover(book.contentId, full = false, target = target) }
        }
        if (peer.mirror) mirror(peers.peer(peer.peerId) ?: peer, page.folders)
    }

    // Mirroring: this library takes the other's changes, newest first wins

    /** The libraries this one mirrors, for our hello. */
    fun mirroring(): List<String> = peers.peers().filter { it.mirror }.map { it.peerId }

    /**
     * Turns mirroring of [peerId] on or off. On, the next refresh reads its whole catalog
     * again, so everything there is considered once; removals are only followed from now.
     */
    fun setMirror(peerId: String, on: Boolean) {
        val peer = peers.peer(peerId) ?: return
        peers.savePeer(peer.copy(mirror = on, mirrorSince = if (on) clock() else null, cursor = if (on) 0 else peer.cursor))
        refreshViews()
    }

    /** What mirroring [peerId] would do here first, from its last known catalog. */
    fun mirrorPreview(peerId: String): MirrorPreview {
        val store = store ?: return MirrorPreview(0, 0, 0)
        val links = peers.links(peerId)
        var copy = 0
        var bytes = 0L
        var move = 0
        peers.books(peerId).forEach { theirs ->
            val ours = counterpart(store, links, theirs)
            when {
                ours == null -> {
                    copy++
                    bytes += theirs.catalog.sizeBytes
                }
                theirs.catalog.folderStamp > ours.catalog.folderStamp && theirs.catalog.folder != ours.catalog.folder -> move++
            }
        }
        return MirrorPreview(copy, bytes, move)
    }

    /** Our book for one of theirs: linked before, or the same file by any id either has had. */
    private fun counterpart(store: Store, links: Map<String, String>, theirs: LibraryBook): LibraryBook? {
        links[theirs.catalog.contentId]?.let { store.book(store.resolve(it)) }?.let { return it }
        return (listOf(theirs.catalog.contentId) + theirs.catalog.previousIds).firstNotNullOfOrNull { store.book(store.resolve(it)) }
    }

    private fun followRemovals(peer: Peer, removed: Map<String, Stamp>) {
        val store = store ?: return
        val since = peer.mirrorSince ?: return
        val links = peers.links(peer.peerId)
        removed.forEach { (theirId, stamp) ->
            val ours = links[theirId]?.let { store.book(store.resolve(it)) } ?: return@forEach
            // Only what went after mirroring began, and only if nothing newer happened to it here.
            if (stamp.at < since || stamp <= maxOf(ours.catalog.folderStamp, ours.catalog.infoStamp)) return@forEach
            store.stampedAs(stamp) { library.trash(listOf(ours.catalog.contentId), trash) }
            peers.unlink(peer.peerId, theirId)
        }
    }

    private fun mirror(peer: Peer, folders: List<FolderStamp>) {
        val store = store ?: return
        val client = client(peer)
        // Folders first, so empty ones and renames are in place before books arrive.
        folders.sortedBy { it.stamp }.forEach { f ->
            runCatching { library.apply(OrganizeChange.Folder(f.path, f.removed, f.stamp.at), f.stamp) }
        }
        val links = peers.links(peer.peerId)
        val theirs = peers.books(peer.peerId)
        val missing = theirs.count { counterpart(store, links, it) == null }
        var copied = 0
        for (book in theirs) {
            val c = book.catalog
            var ours = counterpart(store, links, book)
            if (ours == null) {
                // Removed here since: our removal travels to them; don't bring it back.
                val goneHere = (listOf(c.contentId) + c.previousIds).firstNotNullOfOrNull { store.removedStamp(store.resolve(it)) }
                if (goneHere != null && goneHere > maxOf(c.folderStamp, c.infoStamp)) continue
                onActivity("Mirroring ${peer.name}: copying ${++copied} of $missing")
                // Copied in with their folder stamp, so it doesn't look like a move of ours.
                val result = runCatching { store.stampedAs(c.folderStamp) { copy(book, c.folder) } }.getOrNull() ?: continue
                if (result !is CopyResult.Added) continue
                ours = store.book(store.resolve(c.contentId)) ?: continue
            }
            peers.link(peer.peerId, c.contentId, ours.catalog.contentId)
            if (c.folderStamp > ours.catalog.folderStamp && c.folder != ours.catalog.folder) {
                runCatching { library.apply(OrganizeChange.BookFolder(ours.catalog.contentId, c.folder, c.folderStamp.at), c.folderStamp) }
            }
            if (c.infoStamp > ours.catalog.infoStamp) takeDetails(client, store, ours, book)
        }
        onActivity(null)
    }

    /** Their newer details (and cover) for our copy, keeping their stamp. */
    private fun takeDetails(client: PeerClient, store: Store, ours: LibraryBook, theirs: LibraryBook) {
        val c = theirs.catalog
        val current = store.book(ours.catalog.contentId) ?: return
        val etag = c.coverEtag?.let { _ ->
            val part = temp.resolve("mirror-cover-${c.contentId}.jpg")
            try {
                if (runCatching { client.cover(c.contentId, full = true, target = part) }.getOrDefault(false)) {
                    library.saveCover(current.catalog.contentId, part.toFile().readBytes())
                } else {
                    null
                }
            } finally {
                Files.deleteIfExists(part)
            }
        } ?: current.catalog.coverEtag
        store.stampedAs(c.infoStamp) {
            store.saveInfo(
                current.copy(
                    catalog = current.catalog.copy(
                        title = c.title,
                        author = c.author,
                        genres = c.genres,
                        description = c.description,
                        year = c.year,
                        series = c.series,
                        seriesIndex = c.seriesIndex,
                        coverEtag = etag,
                    ),
                    genreSource = "edited",
                    titleFromFile = false,
                    // Already settled over there: no lookup here to overrule it.
                    lookup = BookInfoService.DONE,
                ),
            )
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
        // The same file name as over there, or one made from the book.
        val named = temp.resolve("copy-$id").createDirectories()
            .resolve(Library.safeFileName(book.catalog.fileName ?: listOfNotNull(book.catalog.author, book.catalog.title).joinToString(" - "), book.catalog.format))
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
