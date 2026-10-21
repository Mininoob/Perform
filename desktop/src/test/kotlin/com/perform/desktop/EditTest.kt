package com.perform.desktop

import com.perform.sync.Format
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes

/** Details edited by hand land in the catalog and, when switched on, in the file. */
class EditTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var paths: AppPaths
    private lateinit var service: LibraryService

    private fun start(writeIntoFiles: Boolean) {
        AppConfig("srv", "PC", 0, root.toString(), "pw", lookUpOnline = false, writeIntoFiles = writeIntoFiles).save(paths)
        service = LibraryService(paths)
        service.rescan()
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("perform-edit")
        root = dir.resolve("Books").createDirectories()
        paths = AppPaths(dir.resolve("data"))
    }

    @After
    fun tearDown() {
        service.stop()
        dir.toFile().deleteRecursively()
    }

    private fun only() = service.books.value.single()

    private val edits = BookEdits(
        title = "Dune",
        author = "Frank Herbert",
        series = "Dune Chronicles",
        seriesIndex = 1.0,
        year = 1965,
        genres = listOf("Science Fiction"),
        description = "Spice.",
    )

    @Test
    fun `an edited EPUB carries every field, series both ways, and keeps its original`() = runBlocking {
        TestBooks.epub(root.resolve("dune.epub"), "DUNE (retail)", author = "Herbert", subjects = listOf("Fantasy"))
        start(writeIntoFiles = true)
        val before = only()
        val original = root.resolve("dune.epub").readBytes()

        val newId = service.saveEdits(before, edits)

        val after = only().catalog
        assertNotEquals(before.catalog.contentId, newId)
        assertEquals(newId, after.contentId)
        assertEquals("Dune", after.title)
        assertEquals("Dune Chronicles", after.series)
        assertEquals(1.0, after.seriesIndex)
        assertTrue(paths.backups.resolve("${before.catalog.contentId}.epub").readBytes().contentEquals(original))

        // The file now says exactly what was typed, replacing what it said before.
        val reread = BookMetadata.read(root.resolve("dune.epub"), Format.EPUB)
        assertEquals("Dune", reread.title)
        assertEquals("Frank Herbert", reread.author)
        assertEquals(listOf("Science Fiction"), reread.subjects)
        assertEquals("Spice.", reread.description)
        assertEquals(1965, reread.year)
        assertEquals("Dune Chronicles", reread.series)
        assertEquals(1.0, reread.seriesIndex)
        ZipFile(root.resolve("dune.epub").toFile()).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf, "calibre:series" in opf && "belongs-to-collection" in opf)
            assertTrue(opf, "Herbert</dc:creator>" in opf && "retail" !in opf)
        }
    }

    @Test
    fun `a new cover replaces the old one in the file and the catalog`() = runBlocking {
        TestBooks.epub(root.resolve("dune.epub"), "Dune")
        start(writeIntoFiles = true)
        val before = only()

        service.saveEdits(before, edits.copy(cover = TestBooks.jpeg()))
        service.saveEdits(only(), edits.copy(cover = TestBooks.jpeg())) // twice: still one cover

        assertNotEquals(before.catalog.coverEtag, null)
        ZipFile(root.resolve("dune.epub").toFile()).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertEquals(opf, 1, Regex("cover-image").findAll(opf).count())
            assertEquals(1, zip.entries().toList().count { it.name.endsWith("perform-cover.jpg") })
        }
    }

    @Test
    fun `an edited PDF gets its document properties replaced`() = runBlocking {
        val file = root.resolve("dune.pdf")
        TestBooks.pdf(file)
        start(writeIntoFiles = true)

        service.saveEdits(only(), edits.copy(description = "", author = "Frank Herbert"))

        Loader.loadPDF(file.toFile()).use { pdf ->
            val info = pdf.documentInformation
            assertEquals("Dune", info.title)
            assertEquals("Frank Herbert", info.author)
            assertNull(info.subject) // cleared
            assertEquals("Science Fiction", info.keywords)
            assertEquals("Dune Chronicles", info.getCustomMetadataValue("Series"))
            assertEquals("1", info.getCustomMetadataValue("SeriesIndex"))
        }
        assertEquals("Dune Chronicles", BookMetadata.read(file, Format.PDF).series)
    }

    @Test
    fun `with writing off, edits stay in the library and the file is untouched`() = runBlocking {
        TestBooks.epub(root.resolve("dune.epub"), "Old title")
        start(writeIntoFiles = false)
        val before = only()
        val bytes = root.resolve("dune.epub").readBytes()

        val id = service.saveEdits(before, edits)

        assertEquals(before.catalog.contentId, id)
        assertEquals("Dune", only().catalog.title)
        assertTrue(root.resolve("dune.epub").readBytes().contentEquals(bytes))
    }

    @Test
    fun `series written by other apps are read`() {
        val file = TestBooks.epub(root.resolve("x.epub"), "X")
        start(writeIntoFiles = true)
        assertNull(BookMetadata.read(file, Format.EPUB).series)
        assertEquals(1965, yearOf("1965-08-01"))
        assertEquals(null, yearOf("unknown"))
    }
}
