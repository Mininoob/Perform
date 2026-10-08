package com.preform.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** Moving books and folders from the organizer keeps files, catalog and phones in step. */
class OrganizeTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library
    private val trashed = mutableListOf<Path>()
    private val fakeTrash: (Path) -> Boolean = { trashed.add(it); Files.delete(it); true }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-organize")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun book(title: String) = store.liveBooks().single { it.catalog.title == title }
    private fun id(title: String) = book(title).catalog.contentId

    @Test
    fun `moving books moves their files and tells phones`() {
        TestBooks.epub(root.resolve("Dune.epub"), "Dune")
        TestBooks.epub(root.resolve("Emma.epub"), "Emma")
        library.scan()
        val cursor = store.currentRev()

        assertEquals(2, library.moveBooks(listOf(id("Dune"), id("Emma")), "Fiction/Classics"))

        assertTrue(root.resolve("Fiction/Classics/Dune.epub").exists())
        assertFalse(root.resolve("Dune.epub").exists())
        assertEquals("Fiction/Classics", book("Dune").catalog.folder)
        assertEquals(2, store.catalogSince(cursor).first.size)
        // A rescan agrees: nothing moved, nothing new.
        assertEquals(Library.ScanResult(0, 0, 0), library.scan())
    }

    @Test
    fun `a name clash gets a number instead of overwriting`() {
        TestBooks.epub(root.resolve("Dune.epub"), "Dune")
        TestBooks.epub(root.resolve("Sci-Fi/Dune.epub"), "Dune Messiah")
        library.scan()

        library.moveBooks(listOf(id("Dune")), "Sci-Fi")

        assertTrue(root.resolve("Sci-Fi/Dune (2).epub").exists())
        assertEquals("Sci-Fi/Dune (2).epub", book("Dune").relativePath)
    }

    @Test
    fun `renaming a file keeps its extension and identity`() {
        TestBooks.epub(root.resolve("dune_final_v2.epub"), "Dune")
        library.scan()
        val id = id("Dune")

        assertEquals("Frank Herbert - Dune.epub", library.renameFile(id, "Frank Herbert - Dune"))
        assertEquals("Frank Herbert - Dune.epub", store.book(id)!!.relativePath)
    }

    @Test
    fun `renaming a folder carries every book inside, however deep`() {
        TestBooks.epub(root.resolve("SF/Dune.epub"), "Dune")
        TestBooks.epub(root.resolve("SF/Classic/Foundation.epub"), "Foundation")
        TestBooks.epub(root.resolve("SFX/Other.epub"), "Other")
        library.scan()
        val cursor = store.currentRev()

        assertEquals("Science Fiction", library.renameFolder("SF", "Science Fiction"))

        assertEquals("Science Fiction", book("Dune").catalog.folder)
        assertEquals("Science Fiction/Classic", book("Foundation").catalog.folder)
        assertEquals("SFX", book("Other").catalog.folder) // only an exact folder prefix moves
        assertEquals(2, store.catalogSince(cursor).first.size)
        assertEquals(Library.ScanResult(0, 0, 0), library.scan())
    }

    @Test
    fun `changing only a folder's capitals works`() {
        TestBooks.epub(root.resolve("fiction/Dune.epub"), "Dune")
        library.scan()
        library.renameFolder("fiction", "Fiction")
        assertEquals("Fiction", book("Dune").catalog.folder)
        assertEquals(listOf("Fiction"), library.folders())
    }

    @Test
    fun `deleting a folder moves its books up and keeps anything that isn't a book`() {
        TestBooks.epub(root.resolve("Old/Sub/Dune.epub"), "Dune")
        library.scan()
        library.deleteFolder("Old/Sub")
        assertEquals("Old", book("Dune").catalog.folder)
        assertFalse(root.resolve("Old/Sub").exists())

        root.resolve("Old/notes.txt").writeText("mine")
        assertThrows(OrganizeException::class.java) { library.deleteFolder("Old") }
        assertEquals("", book("Dune").catalog.folder) // the book still came out
        assertTrue(root.resolve("Old/notes.txt").exists())
    }

    @Test
    fun `new folders, and names that would escape the library`() {
        assertEquals("Fiction", library.createFolder("", "Fiction"))
        assertEquals("Fiction/Fantasy", library.createFolder("Fiction", "Fantasy"))
        assertTrue(root.resolve("Fiction/Fantasy").exists())
        assertThrows(OrganizeException::class.java) { library.createFolder("", "Fiction") }
        assertEquals("_ escape", Library.safeFolderName("../ escape"))
        assertThrows(OrganizeException::class.java) { library.moveBooks(emptyList(), "../outside") }
        assertThrows(OrganizeException::class.java) { library.createFolder("", "  ") }
    }

    @Test
    fun `importing copies books in and skips ones already there`() {
        val outside = dir.resolve("Downloads").createDirectories()
        TestBooks.epub(outside.resolve("Dune.epub"), "Dune")
        TestBooks.epub(root.resolve("Emma.epub"), "Emma")
        library.scan()
        Files.copy(root.resolve("Emma.epub"), outside.resolve("Emma copy.epub"))
        outside.resolve("notes.txt").writeText("hi")

        val result = library.import(
            listOf(outside.resolve("Dune.epub"), outside.resolve("Emma copy.epub"), outside.resolve("notes.txt")),
            "New",
        )

        assertEquals(Library.ImportResult(added = 1, alreadyThere = 1, notBooks = 1), result)
        assertTrue(root.resolve("New/Dune.epub").exists())
        assertTrue(outside.resolve("Dune.epub").exists()) // copied, not moved
        assertEquals("New", book("Dune").catalog.folder)
    }

    @Test
    fun `books go to the bin, never deleted, and leave the catalog`() {
        TestBooks.epub(root.resolve("Dune.epub"), "Dune")
        library.scan()
        val id = id("Dune")

        assertEquals(0, library.trash(listOf(id)) { false }) // the bin refused: nothing happens
        assertTrue(root.resolve("Dune.epub").exists())

        assertEquals(1, library.trash(listOf(id), fakeTrash))
        assertEquals(listOf(root.resolve("Dune.epub").toAbsolutePath().normalize()), trashed)
        assertTrue(store.liveBooks().isEmpty())
    }

    @Test
    fun `an identical copy elsewhere is a duplicate, not a move`() {
        TestBooks.epub(root.resolve("Dune.epub"), "Dune")
        library.scan()
        Files.copy(root.resolve("Dune.epub"), root.resolve("Copy of Dune.epub"))

        repeat(3) { assertEquals(Library.ScanResult(0, 0, 0), library.scan()) }

        assertEquals("Dune.epub", book("Dune").relativePath)
        assertEquals(mapOf(id("Dune") to listOf("Copy of Dune.epub")), library.identicalCopies)

        assertTrue(library.trashCopy("Copy of Dune.epub", fakeTrash))
        assertTrue(library.identicalCopies.isEmpty())
        assertTrue(root.resolve("Dune.epub").exists())
    }

    @Test
    fun `hidden folders are ignored`() {
        TestBooks.epub(root.resolve(".trash/Dune.epub"), "Dune")
        library.scan()
        assertTrue(store.liveBooks().isEmpty())
        assertTrue(library.folders().isEmpty())
    }
}
