package com.preform.desktop

import com.preform.sync.CatalogBook
import com.preform.sync.Format
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class AutoOrganizeTest {

    private fun book(
        path: String,
        title: String,
        author: String? = null,
        genres: List<String> = emptyList(),
        series: String? = null,
        index: Double? = null,
    ) = LibraryBook(
        catalog = CatalogBook(
            contentId = path.hashCode().toString(),
            title = title,
            author = author,
            format = if (path.endsWith(".pdf")) Format.PDF else Format.EPUB,
            sizeBytes = 1,
            addedAt = 0,
            folder = folderOf(path),
            genres = genres,
            series = series,
            seriesIndex = index,
        ),
        relativePath = path,
        modified = 0,
    )

    private val hobbit = book("Downloads/hobbit.epub", "The Hobbit", "J.R.R. Tolkien", listOf("Fantasy", "Children's"))
    private val dune = book("dune_v2.epub", "Dune", "Frank Herbert", listOf("Science Fiction"), "Dune Chronicles", 1.0)
    private val manual = book("scan001.pdf", "Manual")

    private fun targets(plan: List<PlannedMove>) = plan.associate { it.title to it.to }

    @Test
    fun `by genre uses each book's first genre, Uncategorized otherwise`() {
        val plan = planOrganize(listOf(hobbit, dune, manual), OrganizeScheme.BY_GENRE, rename = false)
        assertEquals(
            mapOf("The Hobbit" to "Fantasy/hobbit.epub", "Dune" to "Science Fiction/dune_v2.epub", "Manual" to "Uncategorized/scan001.pdf"),
            targets(plan),
        )
    }

    @Test
    fun `author then series, and renaming to Author - Title`() {
        val plan = planOrganize(listOf(hobbit, dune, manual), OrganizeScheme.AUTHOR_SERIES, rename = true)
        assertEquals("Frank Herbert/Dune Chronicles/Frank Herbert - Dune (Dune Chronicles 1).epub", targets(plan)["Dune"])
        assertEquals("J.R.R. Tolkien/J.R.R. Tolkien - The Hobbit.epub", targets(plan)["The Hobbit"])
        assertEquals("Unknown Author/Manual.pdf", targets(plan)["Manual"])
    }

    @Test
    fun `renaming only keeps folders`() {
        val plan = planOrganize(listOf(hobbit), scheme = null, rename = true)
        assertEquals("Downloads/J.R.R. Tolkien - The Hobbit.epub", targets(plan)["The Hobbit"])
    }

    @Test
    fun `a book already in place isn't listed, and a scope folder is respected`() {
        val placed = book("Fantasy/hobbit.epub", "The Hobbit", "J.R.R. Tolkien", listOf("Fantasy"))
        assertTrue(planOrganize(listOf(placed), OrganizeScheme.BY_GENRE, rename = false).isEmpty())
        assertEquals("Fiction/Fantasy/hobbit.epub", targets(planOrganize(listOf(placed), OrganizeScheme.BY_GENRE, false, base = "Fiction"))["The Hobbit"])
    }

    @Test
    fun `names never collide with each other or with what stays`() {
        val a = book("a/Dune.epub", "Dune", "Frank Herbert", listOf("Science Fiction"))
        val b = book("b/Dune.epub", "Dune", "Frank Herbert", listOf("Science Fiction"))
        val plan = planOrganize(listOf(a, b), OrganizeScheme.BY_AUTHOR, rename = true, occupied = setOf("Frank Herbert/Frank Herbert - Dune.epub"))
        assertEquals(
            setOf("Frank Herbert/Frank Herbert - Dune (2).epub", "Frank Herbert/Frank Herbert - Dune (3).epub"),
            plan.map { it.to }.toSet(),
        )
    }

    // Applying a plan to real files.

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-auto")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `applying moves the files, clears emptied folders, and undo puts everything back`() {
        TestBooks.epub(root.resolve("Downloads/x/hobbit.epub"), "The Hobbit", author = "J.R.R. Tolkien", subjects = listOf("Fantasy"))
        TestBooks.epub(root.resolve("Keep/dune.epub"), "Dune", author = "Frank Herbert", subjects = listOf("Science fiction"))
        root.resolve("Keep/notes.txt").writeText("mine")
        library.scan()
        val before = store.liveBooks().associate { it.catalog.title to it.relativePath }

        val plan = planOrganize(store.liveBooks(), OrganizeScheme.BY_GENRE, rename = true)
        val applied = library.applyMoves(plan)

        assertEquals(2, applied.size)
        assertTrue(root.resolve("Fantasy/J.R.R. Tolkien - The Hobbit.epub").exists())
        assertTrue(root.resolve("Science Fiction/Frank Herbert - Dune.epub").exists())
        assertFalse("emptied folders go", root.resolve("Downloads").exists())
        assertTrue("a folder with other files stays", root.resolve("Keep/notes.txt").exists())
        assertEquals(Library.ScanResult(0, 0, 0), library.scan())

        library.undoMoves(applied)

        assertEquals(before, store.liveBooks().associate { it.catalog.title to it.relativePath })
        assertTrue(root.resolve("Downloads/x/hobbit.epub").exists())
        assertFalse(root.resolve("Fantasy").exists())
    }
}
