package com.preform.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.preform.desktop.ui.CoverCache
import com.preform.desktop.ui.LibraryWindow
import com.preform.desktop.ui.PreformTheme
import com.preform.desktop.ui.WindowState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** The organizer window, driven with real mouse input. */
@OptIn(ExperimentalTestApi::class)
class WindowTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var service: LibraryService

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-window")
        root = dir.resolve("Books").createDirectories()
        TestBooks.epub(root.resolve("Dune.epub"), "Dune", author = "Frank Herbert")
        TestBooks.epub(root.resolve("Emma.epub"), "Emma", author = "Jane Austen")
        root.resolve("Fiction").createDirectories()
        val paths = AppPaths(dir.resolve("data"))
        AppConfig("srv", "PC", 0, root.toString(), "pw", lookUpOnline = false).save(paths)
        service = LibraryService(paths)
        service.rescan()
    }

    @After
    fun tearDown() {
        service.stop()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `dragging a book onto a folder moves its file`() = runComposeUiTest {
        val state = WindowState()
        setContent {
            PreformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, CoverCache(service.library), state) }
        }
        waitForIdle()

        // Pixel bounds of the sidebar's "Fiction" row and of Dune's title on its card.
        val folder = onAllNodesWithText("Fiction").onFirst().fetchSemanticsNode().boundsInRoot
        val card = onNodeWithText("Dune").fetchSemanticsNode().boundsInRoot
        val toFolder = folder.center - card.center
        // Grab the card and drag it over the folder in the sidebar, in small steps.
        onNodeWithText("Dune").performMouseInput {
            moveTo(center)
            press()
            repeat(12) { step -> moveTo(center + toFolder * ((step + 1) / 12f), delayMillis = 16) }
            release()
        }
        waitUntil(timeoutMillis = 5_000) { root.resolve("Fiction/Dune.epub").exists() }

        assertTrue(root.resolve("Fiction/Dune.epub").exists())
        assertEquals("Fiction", service.books.value.single { it.catalog.title == "Dune" }.catalog.folder)
        // The notice offers to undo it.
        waitUntil(timeoutMillis = 2_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 5_000) { root.resolve("Dune.epub").exists() }
    }

    @Test
    fun `selecting books and pressing Delete asks before using the bin`() = runComposeUiTest {
        val state = WindowState()
        setContent {
            PreformTheme(ThemeBase.PAPER, ThemeAccent.GREEN) { LibraryWindow(service, CoverCache(service.library), state) }
        }
        waitForIdle()
        onNodeWithText("Emma").performClick()
        waitForIdle()
        assertEquals(1, state.selection.size)
        assertTrue(state.onKey(androidx.compose.ui.input.key.Key.Delete, command = false, mac = false))
        waitForIdle()
        onNodeWithText("Move “Emma” to the ${service.platform.trashName}?").assertExists()
        onNodeWithText("Cancel").performClick()
        assertTrue(root.resolve("Emma.epub").exists())
    }
}
