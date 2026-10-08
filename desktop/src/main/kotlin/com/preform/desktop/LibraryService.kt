package com.preform.desktop

import com.preform.sync.PairingInvite
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
import java.nio.file.Path

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
    private val bookInfo = BookInfoService(store, library, OpenLibrary(), paths.backups, { config })
    private val server = LibraryServer(
        ServerDeps(
            store, library, pairing, config.serverId, { config.serverName }, paths.temp,
            lookup = bookInfo::lookUpForPhone,
            lookupCover = bookInfo::cover,
        ),
        identity,
        config.port,
    )
    private val discovery = Discovery(config.serverId, config.serverName, config.port)
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
        _books.value = store.liveBooks()
        _folders.value = library.folders()
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
    }

    private fun status(addresses: List<String> = Addresses.current()) = Status(
        libraryRoot = config.libraryRoot,
        libraryFound = Path.of(config.libraryRoot).toFile().isDirectory,
        devices = store.devices(),
        addresses = addresses,
    )

    private companion object {
        const val RESCAN_MS = 30_000L
    }
}
