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
    fun `auto-scroll is scroll mode that moves on its own`() {
        assertEquals(
            ReaderLayout(scroll = true, spread = false, autoScroll = true, tabletop = false),
            resolveLayout(ReadingMode.AUTO_SCROLL, BookFormat.EPUB, inner, tabletop = false),
        )
    }

    @Test
    fun `tabletop posture is carried through every mode`() {
        ReadingMode.entries.forEach { mode ->
            assertTrue(resolveLayout(mode, BookFormat.EPUB, inner, tabletop = true).tabletop)
        }
    }

    @Test
    fun `600dp is the spread threshold`() {
        assertTrue(resolveLayout(ReadingMode.SPREAD, BookFormat.EPUB, SPREAD_MIN_WIDTH_DP, false).spread)
        assertFalse(resolveLayout(ReadingMode.SPREAD, BookFormat.EPUB, SPREAD_MIN_WIDTH_DP - 1, false).spread)
    }
}
