package com.perform.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadsTest {

    private val count = 12 // pages 0..11

    @Test
    fun `the cover stands alone, then pages pair up as in print`() {
        assertEquals(listOf(0), Spreads.pagesAt(0, count))
        assertEquals(listOf(1, 2), Spreads.pagesAt(1, count))
        assertEquals(listOf(1, 2), Spreads.pagesAt(2, count))
        assertEquals(listOf(9, 10), Spreads.pagesAt(10, count))
        // 12 pages: the last one is left over and stands alone.
        assertEquals(listOf(11), Spreads.pagesAt(11, count))
    }

    @Test
    fun `turning walks whole spreads and stops at the ends`() {
        assertEquals(1, Spreads.next(0, count))
        assertEquals(3, Spreads.next(1, count))
        assertEquals(11, Spreads.next(9, count))
        assertNull(Spreads.next(11, count))

        assertEquals(9, Spreads.previous(11, count))
        assertEquals(0, Spreads.previous(1, count))
        assertNull(Spreads.previous(0, count))
    }

    @Test
    fun `left to right puts the cover on the right and pairs left then right`() {
        assertEquals(null to 0, Spreads.sides(0, count, rightToLeft = false))
        assertEquals(1 to 2, Spreads.sides(1, count, rightToLeft = false))
        assertEquals(11 to null, Spreads.sides(11, count, rightToLeft = false))
    }

    @Test
    fun `right to left mirrors every spread`() {
        assertEquals(0 to null, Spreads.sides(0, count, rightToLeft = true))
        assertEquals(2 to 1, Spreads.sides(1, count, rightToLeft = true))
        assertEquals(null to 11, Spreads.sides(11, count, rightToLeft = true))
    }

    @Test
    fun `out of range pages are clamped, and an empty document has no spreads`() {
        assertEquals(listOf(11), Spreads.pagesAt(99, count))
        assertEquals(emptyList<Int>(), Spreads.pagesAt(0, 0))
        assertNull(Spreads.next(0, 0))
    }
}
