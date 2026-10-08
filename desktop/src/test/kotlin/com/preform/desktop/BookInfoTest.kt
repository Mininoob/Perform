package com.preform.desktop

import com.preform.sync.Format
import com.preform.sync.LookupQuery
import org.apache.pdfbox.Loader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/** Looking books up on Open Library, and writing what's found into the files. */
class BookInfoTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library
    private var config = AppConfig("srv", "PC", 0, "", "pw")
    private val urls = mutableListOf<String>()

    /** A fake Open Library that knows a few books. */
    private val openLibrary = OpenLibrary(
        fetch = { url ->
            urls += url
            when {
                "/works/OL1W.json" in url -> """{"description":{"value":"A desert planet.\n----------\nAlso: [x](y)"}}"""
                "/works/" in url -> """{}"""
                "Dune" in url -> """{"docs":[{"key":"/works/OL1W","title":"Dune","author_name":["Frank Herbert"],
                    "subject":["Science fiction","Deserts"],"first_publish_year":1965,"cover_i":42}]}"""
                "Herbert" in url || "q=" in url -> """{"docs":[{"key":"/works/OL1W","title":"Dune","author_name":["Frank Herbert"],
                    "subject":["Science fiction"],"first_publish_year":1965}]}"""
                else -> """{"docs":[]}"""
            }
        },
        fetchBytes = { TestBooks.jpeg() },
    )

    private lateinit var info: BookInfoService

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-info")
        root = dir.resolve("Books").createDirectories()
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
        info = BookInfoService(store, library, openLibrary, dir.resolve("originals"), { config }, pause = {})
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun only() = store.liveBooks().single()

    @Test
    fun `a lookup fills in what the book lacks and keeps what it has`() {
        config = config.copy(writeIntoFiles = false)
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "F. Herbert", withCover = false)
        library.scan()
        info.lookUpPending()

        val book = only().catalog
        assertEquals("F. Herbert", book.author) // the book's own author wins
        assertEquals(listOf("Science Fiction"), book.genres)
        assertEquals("A desert planet.", book.description)
        assertEquals(1965, book.year)
        assertNotNull("a cover was fetched for a book without one", book.coverEtag)
        assertTrue(store.booksAwaitingLookup().isEmpty())
    }

    @Test
    fun `information is written into the EPUB, the original kept, and the book re-keyed`() {
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "Frank Herbert", withCover = false)
        library.scan()
        val oldId = only().catalog.contentId
        val original = root.resolve("dune.epub").readBytes()

        info.lookUpPending()

        val book = only()
        assertNotEquals(oldId, book.catalog.contentId)
        assertEquals(book.catalog.contentId, store.resolve(oldId))
        assertEquals(listOf(oldId), store.catalogSince(0).books.single().previousIds)
        assertTrue(dir.resolve("originals/$oldId.epub").readBytes().contentEquals(original))

        ZipFile(root.resolve("dune.epub").toFile()).use { zip ->
            assertEquals("mimetype", zip.entries().nextElement().name)
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf, "Science Fiction</dc:subject>" in opf)
            assertTrue(opf, "A desert planet." in opf)
            assertTrue(opf, "1965</dc:date>" in opf)
            assertTrue(opf, "cover-image" in opf)
            assertNotNull(zip.getEntry("OEBPS/preform-cover.jpg"))
        }
        // Still a good book that reads back with its new details.
        val reread = BookMetadata.read(root.resolve("dune.epub"), Format.EPUB)
        assertEquals("Dune", reread.title)
        assertNotNull(reread.cover)

        // A rescan sees nothing new: the row already matches the rewritten file.
        assertEquals(Library.ScanResult(0, 0, 0), library.scan())
    }

    @Test
    fun `putting originals back returns each book to its first id`() {
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "Frank Herbert")
        library.scan()
        val oldId = only().catalog.contentId
        val original = root.resolve("dune.epub").readBytes()
        info.lookUpPending()
        val writtenId = only().catalog.contentId

        assertEquals(1, info.restoreOriginals())

        assertTrue(root.resolve("dune.epub").readBytes().contentEquals(original))
        assertEquals(oldId, only().catalog.contentId)
        assertEquals(oldId, store.resolve(writtenId))
        assertEquals(oldId, store.resolve(oldId))
        assertEquals(0, info.backupCount())
    }

    @Test
    fun `reading progress follows the book to its new id`() {
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "Frank Herbert")
        library.scan()
        val oldId = only().catalog.contentId
        store.mergeState(com.preform.sync.BookState(oldId, lastOpenedAt = 5))

        info.lookUpPending()

        val newId = only().catalog.contentId
        assertNull(store.state(oldId))
        assertEquals(5L, store.state(newId)?.lastOpenedAt)
    }

    @Test
    fun `a book Open Library doesn't know is left untouched`() {
        TestBooks.epub(root.resolve("mystery.epub"), "Unknown Book", author = "Nobody")
        library.scan()
        val before = root.resolve("mystery.epub").readBytes()
        val id = only().catalog.contentId

        info.lookUpPending()

        assertTrue(root.resolve("mystery.epub").readBytes().contentEquals(before))
        assertEquals(id, only().catalog.contentId)
        assertTrue(store.booksAwaitingLookup().isEmpty())
    }

    @Test
    fun `an encrypted EPUB is never rewritten`() {
        val file = TestBooks.epub(root.resolve("drm.epub"), "Dune", author = "Frank Herbert")
        // Add META-INF/encryption.xml, as DRM'd books have.
        val copy = dir.resolve("drm.epub")
        java.util.zip.ZipOutputStream(Files.newOutputStream(copy)).use { out ->
            ZipFile(file.toFile()).use { zip ->
                zip.entries().toList().forEach { e ->
                    out.putNextEntry(java.util.zip.ZipEntry(e.name))
                    out.write(zip.getInputStream(e).readBytes())
                }
            }
            out.putNextEntry(java.util.zip.ZipEntry("META-INF/encryption.xml"))
            out.write("<encryption/>".toByteArray())
        }
        Files.copy(copy, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        library.scan()
        val id = only().catalog.contentId

        info.lookUpPending()

        assertEquals(id, only().catalog.contentId)
        assertEquals(listOf("Science Fiction"), only().catalog.genres) // still in the catalog
        assertFalse(dir.resolve("originals").exists() && info.backupCount() > 0)
    }

    @Test
    fun `PDF information goes into its document properties`() {
        val file = root.resolve("dune.pdf")
        TestBooks.pdf(file)
        // Give it a real title so the fake Open Library recognises it.
        Loader.loadPDF(file.toFile()).use { it.documentInformation.title = "Dune"; it.save(file.toFile()) }
        library.scan()

        info.lookUpPending()

        Loader.loadPDF(file.toFile()).use { pdf ->
            val doc = pdf.documentInformation
            assertEquals("Frank Herbert", doc.author)
            assertEquals("Science Fiction", doc.keywords)
            assertEquals("A desert planet.", doc.subject)
            assertEquals("1965", doc.getCustomMetadataValue("PublicationYear"))
        }
    }

    @Test
    fun `a book named only by its file is identified from the name`() {
        val file = root.resolve("Frank Herbert - Dune.pdf")
        TestBooks.pdf(file)
        Loader.loadPDF(file.toFile()).use { it.documentInformation.title = null; it.save(file.toFile()) }
        config = config.copy(writeIntoFiles = false)
        library.scan()
        assertEquals("Frank Herbert - Dune", only().catalog.title)

        info.lookUpPending()

        assertEquals("Dune", only().catalog.title)
        assertEquals("Frank Herbert", only().catalog.author)
    }

    @Test
    fun `an unreachable Open Library leaves books for next time`() {
        val offline = BookInfoService(store, library, OpenLibrary(fetch = { null }, fetchBytes = { null }), dir.resolve("o"), { config }, pause = {})
        TestBooks.epub(root.resolve("dune.epub"), "Dune")
        library.scan()
        offline.lookUpPending()
        assertEquals(1, store.booksAwaitingLookup().size)
        assertNull(offline.lookUpForPhone(listOf(LookupQuery("k", "Dune"))))
    }

    @Test
    fun `a same-titled book by someone else is not a match`() {
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "Jane Tester")
        config = config.copy(writeIntoFiles = false)
        library.scan()

        info.lookUpPending()

        assertNull(only().catalog.year)
        assertEquals(emptyList<String>(), only().catalog.genres)
    }

    @Test
    fun `phones can look up their own books`() {
        val results = info.lookUpForPhone(listOf(LookupQuery("1", "Dune", "Frank Herbert"), LookupQuery("2", "Nothing Like It")))!!
        assertEquals(listOf(true, false), results.map { it.found })
        assertEquals(1965, results[0].year)
        assertEquals(42L, results[0].coverId)
    }

    @Test
    fun `a phone asking for an old id still gets the book`() {
        TestBooks.epub(root.resolve("dune.epub"), "Dune", author = "Frank Herbert")
        library.scan()
        val oldId = only().catalog.contentId
        info.lookUpPending()
        assertNotNull(library.file(store.book(store.resolve(oldId))!!))
    }
}
