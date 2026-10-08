package com.preform.desktop

import com.preform.sync.Format
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.moveTo

class LibraryTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-lib")
        root = dir.resolve("Books")
        Files.createDirectories(root)
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `a scan finds books with their metadata and covers`() {
        TestBooks.epub(root.resolve("Austen/Pride.epub"), "Pride and Prejudice", "Jane Austen")
        TestBooks.epub(root.resolve("bereshit.epub"), "Bereshit", rtl = true, withCover = false)
        TestBooks.pdf(root.resolve("notes.pdf"))
        Files.writeString(root.resolve("readme.txt"), "not a book")

        assertEquals(3, library.scan().added)

        val books = store.liveBooks().associateBy { it.catalog.title }
        val pride = books.getValue("Pride and Prejudice")
        assertEquals("Jane Austen", pride.catalog.author)
        assertEquals("Austen/Pride.epub", pride.relativePath)
        assertNotNull(library.cover(pride.catalog.contentId, Library.CoverSize.THUMB))
        assertTrue(books.getValue("Bereshit").catalog.rightToLeft)
        assertNull(books.getValue("Bereshit").catalog.coverEtag)
        assertEquals(Format.PDF, books.getValue("Test PDF").catalog.format)
    }

    @Test
    fun `an unchanged library rescans to no changes`() {
        TestBooks.epub(root.resolve("a.epub"), "A")
        library.scan()
        val rev = store.currentRev()

        assertEquals(Library.ScanResult(0, 0, 0), library.scan())
        assertEquals(rev, store.currentRev())
    }

    @Test
    fun `moving a file keeps the book and its identity`() {
        TestBooks.epub(root.resolve("a.epub"), "A")
        library.scan()
        val id = store.liveBooks().single().catalog.contentId

        Files.createDirectories(root.resolve("Shelf"))
        root.resolve("a.epub").moveTo(root.resolve("Shelf/renamed.epub"))

        assertEquals(Library.ScanResult(0, 1, 0), library.scan())
        assertEquals("Shelf/renamed.epub", store.book(id)!!.relativePath)
    }

    @Test
    fun `deleting a file removes the book and tells phones`() {
        TestBooks.epub(root.resolve("a.epub"), "A")
        library.scan()
        val id = store.liveBooks().single().catalog.contentId
        val before = store.currentRev()

        root.resolve("a.epub").deleteExisting()

        assertEquals(1, library.scan().removed)
        val (_, removed, _) = store.catalogSince(before)
        assertEquals(listOf(id), removed)
    }

    @Test
    fun `upload names are made safe for Windows`() {
        assertEquals("a_b_c.epub", Library.safeFileName("a<b>c.epub", Format.EPUB))
        assertEquals("passwd.pdf", Library.safeFileName("../../etc/passwd", Format.PDF))
        assertEquals("Book.epub", Library.safeFileName("C:\\Users\\x\\Book.epub", Format.EPUB))
        assertEquals("book.epub", Library.safeFileName("...", Format.EPUB))
    }

    @Test
    fun `only private networks may connect`() {
        fun allowed(ip: String) = PrivateNetwork.isAllowed(InetAddress.getByName(ip))
        assertTrue(allowed("127.0.0.1"))
        assertTrue(allowed("192.168.1.42"))
        assertTrue(allowed("10.0.0.5"))
        assertTrue(allowed("172.20.1.1"))
        assertTrue(allowed("100.101.102.103")) // Tailscale
        assertTrue(allowed("fd7a:115c:a1e0::1")) // Tailscale IPv6
        assertFalse(allowed("8.8.8.8"))
        assertFalse(allowed("100.128.0.1")) // just outside Tailscale's range
        assertFalse(allowed("2001:4860:4860::8888"))
        assertNull(PrivateNetwork.parse("example.com")) // never a DNS lookup
    }

    @Test
    fun `a pairing code works once, expires, and locks out guessing`() {
        var now = 0L
        val window = PairingWindow { now }
        val code = window.open()
        assertTrue(window.consume(code))
        assertFalse(window.consume(code)) // single use

        val second = window.open()
        now += PairingWindow.VALID_MS
        assertFalse(window.consume(second)) // expired

        val third = window.open()
        repeat(PairingWindow.MAX_FAILURES) { window.consume("000000".takeIf { it != third } ?: "111111") }
        assertFalse(window.consume(third)) // closed after too many wrong guesses
    }
}
