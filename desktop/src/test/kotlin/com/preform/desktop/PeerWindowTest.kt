package com.preform.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.preform.desktop.ui.CoverCache
import com.preform.desktop.ui.LibraryWindow
import com.preform.desktop.ui.Place
import com.preform.desktop.ui.PreformTheme
import com.preform.desktop.ui.WindowState
import com.preform.sync.PeerHello
import com.preform.sync.PeerKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/** Browsing another computer's library in the window, and dragging a book over to copy it. */
@OptIn(ExperimentalTestApi::class)
class PeerWindowTest {

    private lateinit var dir: Path
    private lateinit var macBooks: Path
    private lateinit var macStore: Store
    private lateinit var mac: LibraryServer
    private lateinit var macPairing: PairingWindow
    private var macPort = 0
    private lateinit var pcBooks: Path
    private lateinit var service: LibraryService

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-peer-window")
        macBooks = dir.resolve("mac/Books").createDirectories()
        TestBooks.epub(macBooks.resolve("Fiction/Dune.epub"), "Dune", "Frank Herbert")
        TestBooks.epub(macBooks.resolve("Fiction/Emma.epub"), "Emma", "Jane Austen")
        macStore = Store(dir.resolve("mac/library.db"))
        val macLibrary = Library({ macBooks }, macStore, dir.resolve("mac/covers"))
        macLibrary.scan()
        macPairing = PairingWindow()
        macPort = ServerSocket(0).use { it.localPort }
        mac = LibraryServer(
            ServerDeps(macStore, macLibrary, macPairing, "mac-id", { "Studio Mac" }, dir.resolve("mac/temp").createDirectories(),
                hello = { PeerHello("mac-id", "Studio Mac", PeerKind.COMPUTER, "macos", 2) }),
            TlsIdentity(dir.resolve("mac/identity.jks"), "pw"),
            macPort,
        )
        mac.start()

        // The PC has the very same Emma file, and a Classics folder.
        pcBooks = dir.resolve("pc/Books").createDirectories()
        Files.copy(macBooks.resolve("Fiction/Emma.epub"), pcBooks.resolve("Emma.epub"))
        pcBooks.resolve("Classics").createDirectories()
        val paths = AppPaths(dir.resolve("pc/data"))
        AppConfig("pc-id", "Study PC", 0, pcBooks.toString(), "pw", lookUpOnline = false, writeIntoFiles = false).save(paths)
        service = LibraryService(paths)
        service.rescan()

        val (hello, cert) = service.peers.hello("127.0.0.1", macPort)
        service.peers.connect(listOf("127.0.0.1"), macPort, cert, macPairing.open(), hello)
    }

    @After
    fun tearDown() {
        service.stop()
        mac.stop()
        macStore.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `their shelves show what's in yours, and a dragged book is copied in`() = runComposeUiTest {
        val state = WindowState()
        setContent {
            PreformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, CoverCache(service.library, service.peers::cover), state) }
        }
        waitForIdle()
        state.go(Place.Peer("mac-id"))
        waitForIdle()

        onNodeWithText("Studio Mac").assertExists()
        // Emma is the same file as ours; Dune isn't here at all.
        onNodeWithText("In yours").assertExists()
        onNodeWithText("Not in yours").assertExists()
        assertEquals(1, onAllNodesWithText("In yours").fetchSemanticsNodes().size)

        val folder = onAllNodesWithText("Classics").onFirst().fetchSemanticsNode().boundsInRoot
        val card = onNodeWithText("Dune").fetchSemanticsNode().boundsInRoot
        val toFolder = folder.center - card.center
        onNodeWithText("Dune").performMouseInput {
            moveTo(center)
            press()
            repeat(12) { step -> moveTo(center + toFolder * ((step + 1) / 12f), delayMillis = 16) }
            release()
        }
        waitUntil(timeoutMillis = 10_000) { service.books.value.any { it.catalog.title == "Dune" && it.catalog.folder == "Classics" } }
        assertEquals(1, pcBooks.resolve("Classics").listDirectoryEntries().size)
        // Copied, not moved: the Mac still has it.
        assertTrue(macBooks.resolve("Fiction/Dune.epub").exists())
    }

    /** Renders the peer screens for review: `-Dscreenshots.data=any` turns it on. */
    @Test
    fun render() {
        org.junit.Assume.assumeTrue(!System.getProperty("screenshots.data").isNullOrBlank())
        val out = Path.of("build/screenshots").createDirectories()
        val covers = CoverCache(service.library, service.peers::cover)
        fun shot(name: String, setup: WindowState.() -> Unit) {
            val state = WindowState().apply(setup)
            val scene = androidx.compose.ui.ImageComposeScene(1240, 820, androidx.compose.ui.unit.Density(1f)) {
                PreformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, covers, state) }
            }
            repeat(10) { i -> scene.render(i * 100_000_000L); Thread.sleep(100) }
            java.nio.file.Files.write(out.resolve("$name.png"), scene.render(2_000_000_000L).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
        shot("peer-browse") { place = Place.Peer("mac-id") }
        shot("peer-compare") { place = Place.Compare("mac-id", theirs = true) }
        shot("devices") { place = Place.Devices }
    }
}
