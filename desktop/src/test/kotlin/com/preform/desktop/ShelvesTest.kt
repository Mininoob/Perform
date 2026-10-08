package com.preform.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.moveTo

/** Folders become shelves on the phone; genres come from the book or from Open Library. */
class ShelvesTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-shelves")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun catalog() = store.catalogSince(0).first.associateBy { it.title }

    @Test
    fun `the catalog carries each book's folder`() {
        TestBooks.epub(root.resolve("Fiction/Fantasy/Hobbit.epub"), "The Hobbit")
        TestBooks.epub(root.resolve("Loose.epub"), "Loose")
        library.scan()

        assertEquals("Fiction/Fantasy", catalog().getValue("The Hobbit").folder)
        assertEquals("", catalog().getValue("Loose").folder)
    }

    @Test
    fun `moving a book to another folder tells the phones`() {
        TestBooks.epub(root.resolve("Unsorted/Dune.epub"), "Dune")
        library.scan()
        val cursor = store.currentRev()

        root.resolve("Sci-Fi").createDirectories()
        root.resolve("Unsorted/Dune.epub").moveTo(root.resolve("Sci-Fi/Dune.epub"))
        library.scan()

        val (changed, _, _) = store.catalogSince(cursor)
        assertEquals("Sci-Fi", changed.single().folder)
    }

    @Test
    fun `renaming within a folder is not news`() {
        TestBooks.epub(root.resolve("Sci-Fi/dune.epub"), "Dune")
        library.scan()
        val cursor = store.currentRev()

        root.resolve("Sci-Fi/dune.epub").moveTo(root.resolve("Sci-Fi/Dune (1965).epub"))
        library.scan()

        assertTrue(store.catalogSince(cursor).first.isEmpty())
    }

    @Test
    fun `genres declared in the book are used directly`() {
        TestBooks.epub(root.resolve("a.epub"), "A", subjects = listOf("FICTION / Fantasy / Epic"))
        TestBooks.epub(root.resolve("b.epub"), "B")
        library.scan()

        assertEquals(listOf("Fantasy"), catalog().getValue("A").genres)
        // Nothing in B: it waits for the online lookup.
        assertEquals(listOf("B"), store.booksAwaitingGenres().map { it.catalog.title })
    }

    @Test
    fun `looked-up genres reach the catalog`() {
        TestBooks.epub(root.resolve("b.epub"), "B")
        library.scan()
        val id = store.booksAwaitingGenres().single().catalog.contentId
        val cursor = store.currentRev()

        store.setGenres(id, listOf("Romance"), "online")

        assertEquals(listOf("Romance"), store.catalogSince(cursor).first.single().genres)
        assertTrue(store.booksAwaitingGenres().isEmpty())
    }

    @Test
    fun `open library answers are parsed and classified`() {
        val answer = """{"numFound":1,"docs":[{"title":"Pride and Prejudice","subject":["Fiction","Love stories","Courtship","Romance","Classic Literature"]}]}"""
        val urls = mutableListOf<String>()
        val lookup = GenreLookup { url -> urls += url; answer }

        assertEquals(listOf("Romance", "Classics"), lookup.genres("Pride and Prejudice (Penguin Classics)", "Jane Austen"))
        // Only title and author are sent; the edition note is dropped from the title.
        assertTrue(urls.single().contains("title=Pride+and+Prejudice&author=Jane+Austen"))
    }

    @Test
    fun `a different book with a similar title is ignored`() {
        val answer = """{"docs":[{"title":"The Sample Novel Writer's Handbook","subject":["Science"]}]}"""
        assertEquals(emptyList<String>(), GenreLookup { answer }.genres("Sample Novel", "Jane Tester"))
    }

    @Test
    fun `an unreachable open library means try later`() {
        assertNull(GenreLookup { null }.genres("Dune", "Frank Herbert"))
    }

    @Test
    fun `no match with the author falls back to the title alone`() {
        val lookup = GenreLookup { url ->
            if ("author=" in url) """{"docs":[]}""" else """{"docs":[{"title":"Dune","subject":["Science fiction"]}]}"""
        }
        assertEquals(listOf("Science Fiction"), lookup.genres("Dune", "F. Herbert"))
    }
}
