package com.perform.reader.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDiffTest {

    private fun book(uri: String, size: Long = 100, modified: Long = 1) = BookEntity(
        id = uri.hashCode().toLong(),
        uri = uri,
        source = BookSource.FOLDER,
        format = BookFormat.EPUB,
        title = uri,
        author = null,
        sizeBytes = size,
        lastModified = modified,
        coverThumbPath = null,
        coverFullPath = null,
        coverColor = null,
        rightToLeft = false,
        addedAt = 0,
    )

    private fun file(uri: String, size: Long = 100, modified: Long = 1) =
        ScannedFile(uri = uri, name = "$uri.epub", sizeBytes = size, lastModified = modified, format = BookFormat.EPUB)

    @Test
    fun `new files are added`() {
        val diff = diffLibrary(existingFolderBooks = listOf(book("a")), scanned = listOf(file("a"), file("b")))

        assertEquals(listOf("b"), diff.added.map { it.uri })
        assertTrue(diff.changed.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun `files that disappeared are removed`() {
        val diff = diffLibrary(existingFolderBooks = listOf(book("a"), book("b")), scanned = listOf(file("a")))

        assertEquals(listOf("b"), diff.removed.map { it.uri })
    }

    @Test
    fun `a changed size or timestamp counts as changed`() {
        val diff = diffLibrary(
            existingFolderBooks = listOf(book("a"), book("b"), book("c")),
            scanned = listOf(file("a"), file("b", size = 200), file("c", modified = 9)),
        )

        assertEquals(listOf("b", "c"), diff.changed.map { it.first.uri })
    }

    @Test
    fun `an unchanged library produces an empty diff`() {
        val diff = diffLibrary(listOf(book("a")), listOf(file("a")))

        assertEquals(LibraryDiff(emptyList(), emptyList(), emptyList()), diff)
    }

    @Test
    fun `formats are recognised by type or extension`() {
        assertEquals(BookFormat.EPUB, formatOf("x.bin", "application/epub+zip"))
        assertEquals(BookFormat.EPUB, formatOf("Novel.EPUB", "application/octet-stream"))
        assertEquals(BookFormat.PDF, formatOf("paper.pdf", null))
        assertNull(formatOf("notes.txt", "text/plain"))
    }
}
