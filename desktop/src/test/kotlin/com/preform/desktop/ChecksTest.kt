package com.preform.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class ChecksTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-checks")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun titles(kind: CheckKind) =
        runChecks(store.liveBooks(), library.identicalCopies)[kind].orEmpty().map { it.catalog.title }.sorted()

    @Test
    fun `each check finds what it says`() {
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "Frank Herbert", subjects = listOf("Science fiction"))
        Files.copy(root.resolve("dune.epub"), root.resolve("dune copy.epub"))
        TestBooks.epub(root.resolve("dune 2nd ed.epub"), "Dune: Deluxe Edition", author = "Herbert, Frank", body = "other", subjects = listOf("Science fiction"))
        TestBooks.epub(root.resolve("nocover.epub"), "Emma", author = "Jane Austen", withCover = false, subjects = listOf("Romance"))
        TestBooks.epub(root.resolve("anon.epub"), "Anonymous Tales", author = "", subjects = listOf("Fantasy"))
        root.resolve("broken.epub").writeBytes("not a zip".toByteArray())
        library.scan()

        assertEquals(listOf("Dune"), titles(CheckKind.IDENTICAL))
        assertEquals(listOf("Dune", "Dune: Deluxe Edition"), titles(CheckKind.SAME_BOOK))
        assertEquals(listOf("Emma"), titles(CheckKind.NO_COVER))
        assertEquals(listOf("Anonymous Tales"), titles(CheckKind.NO_AUTHOR))
        assertEquals(listOf("broken"), titles(CheckKind.UNREADABLE))
        // An unreadable file isn't also reported as missing a cover, author or genre.
        assertTrue("broken" !in titles(CheckKind.NO_COVER) + titles(CheckKind.NO_AUTHOR) + titles(CheckKind.NO_GENRE))
        assertEquals(emptyList<String>(), titles(CheckKind.NO_GENRE))
    }

    @Test
    fun `books without an author are never called the same book`() {
        TestBooks.epub(root.resolve("a.epub"), "Notes", author = "", body = "a")
        TestBooks.epub(root.resolve("b.epub"), "Notes", author = "", body = "b")
        library.scan()
        assertTrue(titles(CheckKind.SAME_BOOK).isEmpty())
    }
}
