package com.perform.desktop

import org.junit.After
import org.junit.Assert.assertEquals
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
        dir = Files.createTempDirectory("perform-shelves")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun catalog() = store.catalogSince(0).books.associateBy { it.title }

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

        assertTrue(store.catalogSince(cursor).books.isEmpty())
    }

    @Test
    fun `genres declared in the book are used directly`() {
        TestBooks.epub(root.resolve("a.epub"), "A", subjects = listOf("FICTION / Fantasy / Epic"))
        library.scan()
        assertEquals(listOf("Fantasy"), catalog().getValue("A").genres)
    }
}
