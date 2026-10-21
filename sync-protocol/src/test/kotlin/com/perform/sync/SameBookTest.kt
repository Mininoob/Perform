package com.perform.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SameBookTest {

    @Test
    fun `the same file is the same book, whatever its title says`() {
        assertEquals(BookMatch.SAME, SameBook.match(listOf("a"), "Dune", null, listOf("x", "a"), "DUNE (retail)", "Herbert"))
    }

    @Test
    fun `same title and author in another file is another edition`() {
        assertEquals(
            BookMatch.EDITION,
            SameBook.match(listOf("a"), "The Hobbit", "J.R.R. Tolkien", listOf("b"), "Hobbit: Illustrated Edition", "Tolkien, J. R. R."),
        )
    }

    @Test
    fun `a different author or no author is never the same book by title`() {
        assertEquals(BookMatch.NONE, SameBook.match(listOf("a"), "Dune", "Frank Herbert", listOf("b"), "Dune", "Jane Tester"))
        assertEquals(BookMatch.NONE, SameBook.match(listOf("a"), "Notes", null, listOf("b"), "Notes", null))
        assertNull(SameBook.key("Notes", null))
    }

    @Test
    fun `pairing requests from older phones still read as phones`() {
        val old = Protocol.json.decodeFromString(PairRequest.serializer(), """{"code":"123456","deviceId":"d","deviceName":"Fold"}""")
        assertEquals(PeerKind.PHONE, old.kind)
    }
}
