package com.preform.reader.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelvesTest {

    private var nextId = 1L

    private fun book(title: String, shelf: String = "", genres: List<String> = emptyList()) = BookEntity(
        id = nextId++,
        uri = "file:///$title",
        source = BookSource.FOLDER,
        format = BookFormat.EPUB,
        title = title,
        author = null,
        sizeBytes = 1,
        lastModified = 0,
        coverThumbPath = null,
        coverFullPath = null,
        coverColor = null,
        rightToLeft = false,
        addedAt = 0,
        shelf = shelf,
        genres = genres.joinToString(GENRE_SEPARATOR),
    )

    private val hobbit = book("Hobbit", "Fiction/Fantasy")
    private val dune = book("Dune", "Fiction/Sci-Fi")
    private val emma = book("Emma", "Fiction")
    private val cosmos = book("Cosmos", "Science")
    private val loose = book("Loose")
    private val all = listOf(hobbit, dune, emma, cosmos, loose)

    @Test
    fun `the top level shows top folders as shelves holding everything below them`() {
        val page = folderPage(all, "")
        assertEquals(listOf("Fiction", "Science"), page.shelves.map { it.name })
        assertEquals(listOf(hobbit, dune, emma), page.shelves[0].books)
        assertEquals(listOf(loose), page.loose)
    }

    @Test
    fun `inside a folder, sub-folders are shelves and its own books are loose`() {
        val page = folderPage(all, "Fiction")
        assertEquals(listOf("Fiction/Fantasy", "Fiction/Sci-Fi"), page.shelves.map { it.key })
        assertEquals(listOf(emma), page.loose)
    }

    @Test
    fun `a folder name that only starts the same is a different shelf`() {
        val fictional = book("X", "Fictional")
        assertEquals(listOf(fictional), folderPage(all + fictional, "").shelves.first { it.name == "Fictional" }.books)
        assertEquals(emptyList<BookEntity>(), folderPage(all + fictional, "Fiction").loose.filter { it == fictional })
    }

    @Test
    fun `genres go fullest first and uncategorized last, two genres means two shelves`() {
        val a = book("A", genres = listOf("Fantasy", "Romance"))
        val b = book("B", genres = listOf("Fantasy"))
        val c = book("C")
        val page = genrePage(listOf(a, b, c), null)
        assertEquals(listOf("Fantasy", "Romance", "Uncategorized"), page.shelves.map { it.name })
        assertEquals(listOf(a, b), page.shelves[0].books)
        assertEquals(listOf(a), genrePage(listOf(a, b, c), "Romance").loose)
        assertEquals(listOf(c), genrePage(listOf(a, b, c), "Uncategorized").loose)
    }

    @Test
    fun `shelf titles read as a path`() {
        assertEquals("Fiction › Fantasy", shelfTitle("Fiction/Fantasy"))
    }
}
