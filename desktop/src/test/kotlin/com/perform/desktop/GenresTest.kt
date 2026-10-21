package com.perform.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.perform.desktop.ui.CoverCache
import com.perform.desktop.ui.Dialog
import com.perform.desktop.ui.LibraryWindow
import com.perform.desktop.ui.PerformTheme
import com.perform.desktop.ui.WindowState
import com.perform.sync.CatalogBook
import com.perform.sync.Format
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

/** The library's own genres, and genres suggested from what the library already knows. */
@OptIn(ExperimentalTestApi::class)
class GenresTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var service: LibraryService

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("perform-genres")
        root = dir.resolve("Books").createDirectories()
        TestBooks.epub(root.resolve("Space Opera/Dune.epub"), "Dune", author = "Frank Herbert")
        TestBooks.epub(root.resolve("Emma.epub"), "Emma", author = "Jane Austen")
        TestBooks.epub(root.resolve("Persuasion.epub"), "Persuasion", author = "Jane Austen")
        val paths = AppPaths(dir.resolve("data"))
        AppConfig("srv", "PC", 0, root.toString(), "pw", lookUpOnline = false, writeIntoFiles = false).save(paths)
        service = LibraryService(paths)
        service.rescan()
    }

    @After
    fun tearDown() {
        service.stop()
        dir.toFile().deleteRecursively()
    }

    private fun book(title: String) = service.books.value.single { it.catalog.title == title }

    @Test
    fun `a genre of the library's own can be made, renamed and removed`() = runBlocking {
        assertEquals("Space Opera", service.addGenre("  Space   Opera "))
        assertNull("Names are unique, whatever the case", service.addGenre("space opera"))
        assertNull("Standard genres are taken", service.addGenre("fantasy"))
        assertTrue("Space Opera" in service.genres())

        service.saveEdits(book("Dune"), BookEdits.of(book("Dune")).copy(genres = listOf("Space Opera")))
        service.refreshLists()
        assertEquals(1, service.renameGenre("Space Opera", "Planetary Romance"))
        service.refreshLists()
        assertEquals(listOf("Planetary Romance"), book("Dune").catalog.genres)
        assertEquals(listOf("Planetary Romance"), service.config.customGenres)

        assertEquals(1, service.removeGenre("Planetary Romance"))
        service.refreshLists()
        assertTrue(book("Dune").catalog.genres.isEmpty())
        assertTrue(service.config.customGenres.isEmpty())
    }

    @Test
    fun `guesses come from the author's other books and from folders named like a genre`() = runBlocking {
        service.saveEdits(book("Emma"), BookEdits.of(book("Emma")).copy(genres = listOf("Romance", "Classics")))
        service.addGenre("Space Opera")
        service.refreshLists()
        val hints = service.genreHints()
        assertEquals("Romance", guessGenres(book("Persuasion"), hints).first())
        assertEquals(listOf("Space Opera"), guessGenres(book("Dune"), hints, max = 1))
    }

    @Test
    fun `auto genre proposes, and applies only what was kept`() = runComposeUiTest {
        service.addGenre("Space Opera")
        val state = WindowState()
        setContent {
            PerformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, CoverCache(service.library), state) }
        }
        waitForIdle()
        state.dialog = Dialog.AutoGenre(service.books.value.filter { it.catalog.genres.isEmpty() })
        waitForIdle()
        // Dune's folder names a genre; the Austens have nothing to go on yet.
        onNodeWithText("Look up the other 2 books online (Open Library)").assertExists()
        onNodeWithText("Give 1 book a genre").performClick()
        waitUntil(timeoutMillis = 5_000) { book("Dune").catalog.genres == listOf("Space Opera") }
        waitUntil(timeoutMillis = 2_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `an empty genre of the library's own shows in the sidebar, ready to drop on`() = runComposeUiTest {
        service.addGenre("To Read Next")
        val state = WindowState()
        setContent {
            PerformTheme(ThemeBase.INK, ThemeAccent.BLUE) { LibraryWindow(service, CoverCache(service.library), state) }
        }
        waitForIdle()
        onNodeWithText("To Read Next").assertExists()
        state.actions!!.addGenre(listOf(book("Emma")), "To Read Next")
        waitUntil(timeoutMillis = 5_000) { "To Read Next" in book("Emma").catalog.genres }
    }

    @Suppress("unused")
    private fun bare(title: String, folder: String) = LibraryBook(
        catalog = CatalogBook(contentId = title, title = title, format = Format.EPUB, sizeBytes = 1, addedAt = 0, folder = folder),
        relativePath = "$folder/$title.epub",
        modified = 0,
    )
}
