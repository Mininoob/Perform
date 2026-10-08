package com.preform.desktop

import com.preform.sync.PairingInvite
import com.preform.sync.PeerHello
import com.preform.sync.PeerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** How far a phone has read a book. */
data class Reading(val progression: Double, val device: String?)

/** What the edit dialog saves. Blank text clears a field (the title is kept). */
data class BookEdits(
    val title: String,
    val author: String,
    val series: String,
    val seriesIndex: Double?,
    val year: Int?,
    val genres: List<String>,
    val description: String,
    /** A new cover image (any common format), or null to keep the current one. */
    val cover: ByteArray? = null,
)

data class Status(
    val libraryRoot: String,
    val libraryFound: Boolean,
    val devices: List<Device>,
    val addresses: List<String>,
    val error: String? = null,
)

/** Everything the app runs: store, scanner, HTTPS server, discovery, lookups, and what the window shows. */
class LibraryService(private val paths: AppPaths, val platform: Platform = Platform.current) {

    private val _config = MutableStateFlow(AppConfig.loadOrCreate(paths, platform))
    val configFlow: StateFlow<AppConfig> = _config
    val config: AppConfig get() = _config.value

    private val store = Store(paths.database)
    private val identity = TlsIdentity(paths.identity, config.keystorePassword)
    private val pairing = PairingWindow()
    val library = Library({ Path.of(config.libraryRoot) }, store, paths.covers)
    private val openLibrary = OpenLibrary()
    private val bookInfo = BookInfoService(store, library, openLibrary, paths.backups, { config })
    private val server = LibraryServer(
        ServerDeps(
            store, library, pairing, config.serverId, { config.serverName }, paths.temp,
            lookup = bookInfo::lookUpForPhone,
            lookupCover = bookInfo::cover,
            hello = {
                PeerHello(config.serverId, config.serverName, PeerKind.COMPUTER, if (platform.isMac) "macos" else "windows", _books.value.size)
            },
        ),
        identity,
        config.port,
    )
    private val discovery = Discovery(config.serverId, config.serverName, config.port)
    private val peerStore = PeerStore(paths.data.resolve("peers.db"))

    /** The other libraries: paired computers and phones that publish their books. */
    val peers = PeerService(peerStore, library, paths.covers, paths.temp, { config.serverId }, { config.serverName })

    /** Other libraries heard on the network. */
    val nearby: StateFlow<List<NearbyLibrary>> get() = discovery.nearby
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanning: Job? = null

    private val _status = MutableStateFlow(status(addresses = emptyList()))
    val status: StateFlow<Status> = _status

    /** Every book in the library, kept current. */
    private val _books = MutableStateFlow(store.liveBooks())
    val books: StateFlow<List<LibraryBook>> = _books

    /** Every folder in the library ("Fiction/Fantasy"), empty ones included. */
    private val _folders = MutableStateFlow(library.folders())
    val folders: StateFlow<List<String>> = _folders

    /** Where phones have got to in each book (from their synced reading state). */
    private val _progress = MutableStateFlow(emptyMap<String, Reading>())
    val progress: StateFlow<Map<String, Reading>> = _progress

    /** Tidy-up checks over the whole library, kept current with it. */
    private val _checks = MutableStateFlow(emptyMap<CheckKind, List<LibraryBook>>())
    val checks: StateFlow<Map<CheckKind, List<LibraryBook>>> = _checks

    /** What the app is busy with, for the status line; null when idle. */
    private val _activity = MutableStateFlow<String?>(null)
    val activity: StateFlow<String?> = _activity

    @OptIn(FlowPreview::class)
    fun start() {
        runCatching { server.start() }.onFailure {
            _status.value = status().copy(error = "Couldn't start on port ${config.port}. Is Preform Library already running?")
            return
        }
        discovery.start()
        // The window follows every change: scans, lookups, phones, edits.
        scope.launch { store.changes.debounce(150).collect { refreshLists() } }
        scope.launch { peerStore.changes.debounce(150).collect { peers.refreshViews() } }
        scope.launch {
            while (isActive) {
                runCatching { peers.refresh() }
                delay(PEER_REFRESH_MS)
            }
        }
        scanning = scope.launch {
            while (isActive) {
                rescan()
                delay(RESCAN_MS)
            }
        }
    }

    /** Re-reads the folder (only new or changed files cost anything), then looks up what's new. */
    fun rescan() {
        _activity.value = "Checking library…"
        runCatching { library.scan() }
        refreshLists()
        _status.value = status()
        if (config.lookUpOnline && store.booksAwaitingLookup().isNotEmpty()) {
            _activity.value = "Looking up book information…"
            runCatching { bookInfo.lookUpPending() }
        }
        _activity.value = null
    }

    fun rescanSoon() {
        scope.launch { rescan() }
    }

    fun refreshLists() {
        val books = store.liveBooks()
        _books.value = books
        _folders.value = library.folders()
        _checks.value = runChecks(books, library.identicalCopies)
        val names = store.devices().associate { it.deviceId to it.name }
        _progress.value = store.statesSince(0).first.mapNotNull { state ->
            state.position?.let { p -> state.contentId to Reading(p.value.progression, names[p.by]) }
        }.toMap()
    }

    // Organizing: run off the UI thread; the lists refresh as soon as each finishes.

    private suspend fun <T> organize(work: () -> T): T = withContext(Dispatchers.IO) {
        try {
            work()
        } finally {
            refreshLists()
        }
    }

    suspend fun moveBooks(contentIds: Collection<String>, folder: String) = organize { library.moveBooks(contentIds, folder) }
    suspend fun renameFile(contentId: String, name: String) = organize { library.renameFile(contentId, name) }
    suspend fun createFolder(parent: String, name: String) = organize { library.createFolder(parent, name) }
    suspend fun renameFolder(path: String, name: String) = organize { library.renameFolder(path, name) }
    suspend fun deleteFolder(path: String) = organize { library.deleteFolder(path) }
    suspend fun import(files: List<Path>, folder: String) = organize { library.import(files, folder) }.also {
        if (it.added > 0) rescanSoon() // looks the new books up
    }
    suspend fun trash(contentIds: Collection<String>) = organize { library.trash(contentIds) }
    suspend fun trashCopy(relativePath: String) = organize { library.trashCopy(relativePath) }

    /**
     * Saves details edited by hand: into the catalog at once (phones see them on their next
     * sync), and into the file too when writing into files is on. Returns the book's id,
     * which changes when the file is rewritten.
     */
    suspend fun saveEdits(book: LibraryBook, edits: BookEdits): String = organize {
        val c = book.catalog
        val coverJpeg = edits.cover?.let(::toJpeg)
        val etag = coverJpeg?.let { library.saveCover(c.contentId, it) } ?: c.coverEtag
        store.saveInfo(
            book.copy(
                catalog = c.copy(
                    title = edits.title.trim().ifBlank { c.title },
                    author = edits.author.trim().ifBlank { null },
                    genres = edits.genres,
                    description = edits.description.trim().ifBlank { null },
                    year = edits.year,
                    series = edits.series.trim().ifBlank { null },
                    seriesIndex = edits.seriesIndex,
                    coverEtag = etag,
                ),
                genreSource = "edited",
                titleFromFile = false,
                lookup = BookInfoService.DONE,
            ),
        )
        if (config.writeIntoFiles) {
            val saved = store.book(c.contentId)!!.catalog
            val fill = BookFill(
                title = saved.title,
                author = saved.author,
                genres = saved.genres,
                description = saved.description,
                year = saved.year,
                cover = coverJpeg,
                series = saved.series,
                seriesIndex = saved.seriesIndex,
                overwrite = true,
            )
            runCatching { library.inject(c.contentId, fill, paths.backups) }
        }
        store.resolve(c.contentId)
    }

    /** A cover for a book from Open Library, by its title and author; null if none is found. */
    suspend fun findCover(title: String, author: String?): ByteArray? = withContext(Dispatchers.IO) {
        openLibrary.find(title, author)?.info?.coverId?.let(openLibrary::cover)
    }

    private fun toJpeg(image: ByteArray): ByteArray? =
        runCatching { javax.imageio.ImageIO.read(image.inputStream()) }.getOrNull()?.let { BookMetadata.jpeg(it, 1600) }

    /** Auto-organize's plan for [books]; nothing moves until [applyPlan]. */
    fun plan(books: List<LibraryBook>, scheme: OrganizeScheme?, rename: Boolean, base: String): List<PlannedMove> =
        planOrganize(books, scheme, rename, base, occupied = _books.value.mapTo(HashSet()) { it.relativePath })

    suspend fun applyPlan(moves: List<PlannedMove>) = organize { library.applyMoves(moves) }
    suspend fun undoPlan(applied: List<Library.AppliedMove>) = organize { library.undoMoves(applied) }

    /** Forgets earlier lookups for these books, so the next pass asks Open Library again. */
    suspend fun lookUpAgain(contentIds: Collection<String>) {
        organize { store.resetLookup(contentIds) }
        rescanSoon()
    }

    // Settings

    private fun updateConfig(change: (AppConfig) -> AppConfig) {
        _config.value = change(config).also { it.save(paths) }
    }

    fun setLookUpOnline(on: Boolean) {
        updateConfig { it.copy(lookUpOnline = on) }
        if (on) rescanSoon()
    }

    fun setWriteIntoFiles(on: Boolean) = updateConfig { it.copy(writeIntoFiles = on) }

    fun setTheme(base: ThemeBase = config.themeBase, accent: ThemeAccent = config.themeAccent) =
        updateConfig { it.copy(themeBase = base, themeAccent = accent) }

    fun setStartAtLogin(on: Boolean) {
        updateConfig { it.copy(startWithWindows = on) }
        platform.setStartAtLogin(on)
    }

    fun setLibraryRoot(folder: Path) {
        updateConfig { it.copy(libraryRoot = folder.toString()) }
        _status.value = status()
        rescanSoon()
    }

    val backupCount: Int get() = bookInfo.backupCount()

    /** Puts every original book file back, and stops writing into files. */
    fun restoreOriginals(): Int {
        setWriteIntoFiles(false)
        return bookInfo.restoreOriginals().also { refreshLists() }
    }

    // Phones

    /** Opens a five-minute pairing window and returns what the QR code shows. */
    fun invite(): PairingInvite = PairingInvite(
        serverId = config.serverId,
        serverName = config.serverName,
        port = config.port,
        certSha256 = identity.fingerprint,
        code = pairing.open(),
        addresses = _status.value.addresses.ifEmpty { Addresses.current() },
    )

    fun closePairing() = pairing.close()

    val pairingOpen: Boolean get() = pairing.isOpen

    fun revoke(deviceId: String) {
        store.revoke(deviceId)
        _status.value = status()
    }

    /** Refreshes devices and addresses (addresses ask the network and Tailscale, so not too often). */
    fun refreshStatus() {
        _status.value = status()
    }

    fun stop() {
        scanning?.cancel()
        discovery.stop()
        server.stop()
        store.close()
        peerStore.close()
    }

    private fun status(addresses: List<String> = Addresses.current()) = Status(
        libraryRoot = config.libraryRoot,
        libraryFound = Path.of(config.libraryRoot).toFile().isDirectory,
        devices = store.devices(),
        addresses = addresses,
    )

    private companion object {
        const val RESCAN_MS = 30_000L
        const val PEER_REFRESH_MS = 120_000L
    }
}
