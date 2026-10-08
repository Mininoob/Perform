package com.preform.desktop

import com.preform.sync.OrganizeChange
import com.preform.sync.Stamp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/** Organizing stamps: every change says when and where it happened, and the newest wins. */
class StampsTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library
    private var now = 1_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-stamps")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db")).also {
            it.origin = "pc"
            it.clock = { now }
        }
        library = Library({ root }, store, dir.resolve("covers"))
        TestBooks.epub(root.resolve("Fiction/Dune.epub"), "Dune", "Frank Herbert")
        TestBooks.epub(root.resolve("Emma.epub"), "Emma", "Jane Austen")
        library.scan()
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun book(title: String) = store.liveBooks().single { it.catalog.title == title }

    @Test
    fun `changes made here are stamped with this library`() {
        now = 2_000
        library.moveBooks(listOf(book("Emma").catalog.contentId), "Classics")
        assertEquals(Stamp(2_000, "pc"), book("Emma").catalog.folderStamp)

        now = 3_000
        library.createFolder("", "Poetry")
        val page = store.catalogSince(0)
        assertEquals(Stamp(3_000, "pc"), page.folders.single { it.path == "Poetry" }.stamp)
        assertTrue(page.books.single { it.title == "Emma" }.folderStamp.at == 2_000L)
    }

    @Test
    fun `a newer change from a phone moves the file and keeps the phone's stamp`() {
        val emma = book("Emma").catalog.contentId
        now = 5_000
        assertTrue(library.apply(OrganizeChange.BookFolder(emma, "Classics/Austen", 4_000), Stamp(4_000, "fold")))
        assertTrue(root.resolve("Classics/Austen/Emma.epub").exists())
        assertEquals(Stamp(4_000, "fold"), book("Emma").catalog.folderStamp)
        // Its new folders reach phones too.
        assertTrue(store.folders().any { it.path == "Classics/Austen" && !it.removed })
    }

    @Test
    fun `an older change loses and changes nothing`() {
        val emma = book("Emma").catalog.contentId
        now = 9_000
        library.moveBooks(listOf(emma), "Classics")
        assertFalse(library.apply(OrganizeChange.BookFolder(emma, "Romance", 8_000), Stamp(8_000, "fold")))
        assertEquals("Classics", book("Emma").catalog.folder)
        assertFalse(root.resolve("Romance").exists())
    }

    @Test
    fun `the same change applied twice is not new the second time`() {
        val emma = book("Emma").catalog.contentId
        val change = OrganizeChange.BookFolder(emma, "Classics", 4_000)
        assertTrue(library.apply(change, Stamp(4_000, "fold")))
        assertFalse(library.apply(change, Stamp(4_000, "fold")))
    }

    @Test
    fun `folders made, renamed and removed elsewhere follow here`() {
        assertTrue(library.apply(OrganizeChange.Folder("To Read", at = 2_000), Stamp(2_000, "fold")))
        assertTrue(root.resolve("To Read").isDirectory())

        assertTrue(library.apply(OrganizeChange.FolderRename("Fiction", "Novels", 3_000), Stamp(3_000, "fold")))
        assertEquals("Novels", book("Dune").catalog.folder)
        assertTrue(store.folder("Fiction")!!.removed)

        // Removing a folder moves its books up, as on the computer.
        assertTrue(library.apply(OrganizeChange.Folder("Novels", removed = true, at = 4_000), Stamp(4_000, "fold")))
        assertEquals("", book("Dune").catalog.folder)
        assertFalse(root.resolve("Novels").exists())
    }

    @Test
    fun `nothing from elsewhere lands outside the library`() {
        val emma = book("Emma").catalog.contentId
        val escaped = runCatching { library.apply(OrganizeChange.BookFolder(emma, "../../Outside", 4_000), Stamp(4_000, "fold")) }
        assertFalse(escaped.getOrDefault(false))
        assertFalse(dir.resolve("Outside").exists())
        assertEquals("", book("Emma").catalog.folder)
        assertFalse(library.apply(OrganizeChange.FolderRename("Fiction", "Elsewhere/Fiction", 5_000), Stamp(5_000, "fold")))
    }

    @Test
    fun `folders made or removed on disk are noticed on the next scan`() {
        root.resolve("Made In Explorer").createDirectories()
        library.scan()
        assertFalse(store.folder("Made In Explorer")!!.removed)
        Files.delete(root.resolve("Made In Explorer"))
        library.scan()
        assertTrue(store.folder("Made In Explorer")!!.removed)
        assertNull(store.folder("Never Existed"))
    }

    @Test
    fun `removals carry their stamp in the catalog`() {
        val cursor = store.currentRev()
        now = 7_000
        library.trash(listOf(book("Emma").catalog.contentId)) { true }
        val page = store.catalogSince(cursor)
        assertEquals(Stamp(7_000, "pc"), page.removedStamps.values.single())
    }
}
