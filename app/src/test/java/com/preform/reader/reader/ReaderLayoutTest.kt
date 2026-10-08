package com.preform.reader.reader

import com.preform.reader.library.BookFormat
import com.preform.reader.settings.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLayoutTest {

    // Z Fold 8 Ultra at 480 dpi: inner display 2256 px (752 dp), cover 1080 px (360 dp).
    private val inner = 752f
    private val cover = 360f

    @Test
    fun `spread shows two pages on the open inner display`() {
        assertTrue(resolveLayout(ReadingMode.SPREAD, BookFormat.EPUB, inner, tabletop = false).spread)
    }

    @Test
    fun `spread falls back to one page on the cover screen`() {
        assertFalse(resolveLayout(ReadingMode.SPREAD, BookFormat.EPUB, cover, tabletop = false).spread)
    }

    @Test
    fun `pdfs never spread`() {
        assertFalse(resolveLayout(ReadingMode.SPREAD, BookFormat.PDF, inner, tabletop = false).spread)
    }

    @Test
    fun `pages mode is one page even when wide`() {
        val layout = resolveLayout(ReadingMode.PAGES, BookFormat.EPUB, inner, tabletop = false)
        assertFalse(layout.spread)
        assertFalse(layout.scroll)
    }

    @Test
    fun `scroll mode scrolls in one column`() {
        assertEquals(
            ReaderLayout(scroll = true, spread = false, tabletop = false),
            resolveLayout(ReadingMode.SCROLL, BookFormat.EPUB, inner, tabletop = false),
        )
    }

    @Test
    fun `tabletop posture is carried through every mode`() {
        ReadingMode.entries.forEach { mode ->
            assertTrue(resolveLayout(mode, BookFormat.EPUB, inner, tabletop = true).tabletop)
        }
    }

    private fun lockFor(mode: ReadingMode, format: BookFormat, pageLock: Boolean = true) =
        resolveLayout(mode, format, cover, tabletop = false, pageLock = pageLock).pdfLock

    @Test
    fun `locked pdf pages hold one whole page`() {
        assertEquals(PdfLock.PAGES, lockFor(ReadingMode.PAGES, BookFormat.PDF))
        // Spread falls back to single pages for PDFs, and those lock the same way.
        assertEquals(PdfLock.PAGES, lockFor(ReadingMode.SPREAD, BookFormat.PDF))
    }

    @Test
    fun `locked pdf scrolling only goes up and down`() {
        assertEquals(PdfLock.SCROLL, lockFor(ReadingMode.SCROLL, BookFormat.PDF))
    }

    @Test
    fun `epubs never need a lock`() {
        ReadingMode.entries.forEach { assertEquals(PdfLock.NONE, lockFor(it, BookFormat.EPUB)) }
    }

    @Test
    fun `lock off leaves pdfs free`() {
        ReadingMode.entries.forEach { assertEquals(PdfLock.NONE, lockFor(it, BookFormat.PDF, pageLock = false)) }
    }

    @Test
    fun `600dp is the spread threshold`() {
        assertTrue(resolveLayout(ReadingMode.SPREAD, BookFormat.EPUB, SPREAD_MIN_WIDTH_DP, false).spread)
        assertFalse(resolveLayout(ReadingMode.SPREAD, BookFormat.EPUB, SPREAD_MIN_WIDTH_DP - 1, false).spread)
    }
}
