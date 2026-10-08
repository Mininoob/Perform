package com.preform.desktop

import com.preform.sync.PairingInvite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Path

data class Status(
    val libraryRoot: String,
    val libraryFound: Boolean,
    val bookCount: Int,
    val devices: List<Device>,
    val addresses: List<String>,
    val error: String? = null,
)

/** Everything the tray app runs: store, scanner, HTTPS server, discovery. */
class LibraryService(private val paths: AppPaths) {

    var config: AppConfig = AppConfig.loadOrCreate(paths)
        private set

    private val store = Store(paths.database)
    private val identity = TlsIdentity(paths.identity, config.keystorePassword)
    private val pairing = PairingWindow()
    private val library = Library({ Path.of(config.libraryRoot) }, store, paths.covers)
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
    private val scope = CoroutineScope(Dispatchers.IO)
    private var scanning: Job? = null

    private val _status = MutableStateFlow(status())
    val status: StateFlow<Status> = _status

    fun start() {
        runCatching { server.start() }.onFailure {
            _status.value = status().copy(error = "Couldn't start on port ${config.port}. Is Preform Library already running?")
            return
        }
        discovery.start()
        // Rescan regularly; each pass only re-reads new or changed files.
        scanning = scope.launch {
            while (isActive) {
                rescan()
                delay(RESCAN_MS)
            }
        }
    }

    fun rescan() {
        runCatching { library.scan() }
        _status.value = status()
        if (config.lookUpOnline) {
            runCatching { bookInfo.lookUpPending() }
            _status.value = status()
        }
    }

    fun setLookUpOnline(on: Boolean) {
        config = config.copy(lookUpOnline = on).also { it.save(paths) }
        if (on) scope.launch { rescan() }
    }

    fun setWriteIntoFiles(on: Boolean) {
        config = config.copy(writeIntoFiles = on).also { it.save(paths) }
    }

    val backupCount: Int get() = bookInfo.backupCount()

    /** Puts every original book file back, and stops writing into files. */
    fun restoreOriginals(): Int {
        setWriteIntoFiles(false)
        return bookInfo.restoreOriginals().also { _status.value = status() }
    }

    /** Opens a five-minute pairing window and returns what the QR code shows. */
    fun invite(): PairingInvite = PairingInvite(
        serverId = config.serverId,
        serverName = config.serverName,
        port = config.port,
        certSha256 = identity.fingerprint,
        code = pairing.open(),
        addresses = Addresses.current(),
    )

    fun closePairing() = pairing.close()

    val pairingOpen: Boolean get() = pairing.isOpen

    fun revoke(deviceId: String) {
        store.revoke(deviceId)
        _status.value = status()
    }

    fun setLibraryRoot(folder: Path) {
        config = config.copy(libraryRoot = folder.toString()).also { it.save(paths) }
        scope.launch { rescan() }
    }

    fun setStartWithWindows(on: Boolean) {
        config = config.copy(startWithWindows = on).also { it.save(paths) }
        Autostart.apply(on)
    }

    fun refresh() {
        _status.value = status()
    }

    fun stop() {
        scanning?.cancel()
        discovery.stop()
        server.stop()
        store.close()
    }

    private fun status() = Status(
        libraryRoot = config.libraryRoot,
        libraryFound = Path.of(config.libraryRoot).toFile().isDirectory,
        bookCount = store.liveBooks().size,
        devices = store.devices(),
        addresses = Addresses.current(),
    )

    private companion object {
        const val RESCAN_MS = 30_000L
    }
}

/** Start with Windows, via the per-user Run key; only possible from the packaged .exe. */
object Autostart {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "Preform Library"

    /** The installed .exe, as jpackage reports it; null when running from a build. */
    val exe: String? get() = System.getProperty("jpackage.app-path")

    fun apply(on: Boolean) {
        val command = if (on) {
            val path = exe ?: return
            listOf("reg", "add", KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"$path\" --background", "/f")
        } else {
            listOf("reg", "delete", KEY, "/v", VALUE, "/f")
        }
        runCatching { ProcessBuilder(command).redirectErrorStream(true).start().waitFor() }
    }
}
