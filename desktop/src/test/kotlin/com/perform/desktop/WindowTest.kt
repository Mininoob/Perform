package com.perform.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.perform.desktop.ui.CoverCache
import com.perform.desktop.ui.LibraryWindow
import com.perform.desktop.ui.PerformTheme
import com.perform.desktop.ui.WindowState
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
        dir = Files.createTempDirectory("perform-window")
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
            PerformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, CoverCache(service.library), state) }
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
    fun `the sorter gives books genres one at a time`() = runComposeUiTest {
        val state = WindowState()
        setContent {
            PerformTheme(ThemeBase.INK, ThemeAccent.BLUE) {
                LibraryWindow(service, CoverCache(service.library), state)
            }
        }
        waitForIdle()
        val books = service.books.value.sortedBy { it.catalog.title }
        state.dialog = com.perform.desktop.ui.Dialog.Sort(books)
        waitForIdle()
        onNodeWithText("1 of 2").assertExists()
        // Dune: Science Fiction, saved; Emma: skipped.
        onNodeWithText("Science Fiction").performClick()
        onNodeWithText("Save and next").performClick()
        waitForIdle()
        onNodeWithText("2 of 2").assertExists()
        onNodeWithText("Skip").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("1 of 2 sorted. The rest are still in Uncategorized.").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { service.books.value.single { it.catalog.title == "Dune" }.catalog.genres == listOf("Science Fiction") }
        assertTrue(service.books.value.single { it.catalog.title == "Emma" }.catalog.genres.isEmpty())
    }

    @Test
    fun `dragging a book onto a genre gives it that genre`() = runComposeUiTest {
        val state = WindowState()
        setContent {
            PerformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, CoverCache(service.library), state) }
        }
        val emma = service.books.value.single { it.catalog.title == "Emma" }
        state.actions!!.setGenres(listOf(emma to listOf("Romance")))
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Romance").fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()

        val genre = onAllNodesWithText("Romance").onFirst().fetchSemanticsNode().boundsInRoot
        val card = onNodeWithText("Dune").fetchSemanticsNode().boundsInRoot
        val toGenre = genre.center - card.center
        onNodeWithText("Dune").performMouseInput {
            moveTo(center)
            press()
            repeat(12) { step -> moveTo(center + toGenre * ((step + 1) / 12f), delayMillis = 16) }
            release()
        }
        waitUntil(timeoutMillis = 5_000) { "Romance" in service.books.value.single { it.catalog.title == "Dune" }.catalog.genres }
        // It stays where it was on disk.
        assertTrue(root.resolve("Dune.epub").exists())
    }

    @Test
    fun `selecting books and pressing Delete asks before using the bin`() = runComposeUiTest {
        val state = WindowState()
        setContent {
            PerformTheme(ThemeBase.PAPER, ThemeAccent.GREEN) { LibraryWindow(service, CoverCache(service.library), state) }
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
