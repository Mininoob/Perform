package com.perform.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class GenresTest {

    @Test
    fun `BISAC subjects use their most specific part`() {
        assertEquals(listOf("Fantasy"), Genres.classify(listOf("FICTION / Fantasy / Epic")))
        assertEquals(listOf("Science Fiction"), Genres.classify(listOf("FICTION / Science Fiction / Space Opera")))
    }

    @Test
    fun `specific beats general`() {
        assertEquals(listOf("Historical Fiction"), Genres.classify(listOf("Historical fiction")))
        assertEquals(listOf("Science Fiction"), Genres.classify(listOf("Science fiction")))
    }

    @Test
    fun `noisy library subjects settle on the most common genre`() {
        // Roughly what Open Library lists for Pride and Prejudice.
        val subjects = listOf(
            "Fiction", "Love stories", "Courtship", "Sisters", "England, fiction", "Romance",
            "Social classes", "Young women", "Classic Literature", "Domestic fiction",
        )
        assertEquals(listOf("Romance", "Classics"), Genres.classify(subjects))
    }

    @Test
    fun `plain fiction only when nothing else matches`() {
        assertEquals(listOf("Fiction"), Genres.classify(listOf("Fiction", "Domestic fiction")))
    }

    @Test
    fun `no recognisable subject means no genre`() {
        assertEquals(emptyList<String>(), Genres.classify(listOf("Sisters", "England")))
        assertEquals(emptyList<String>(), Genres.classify(emptyList()))
    }

    @Test
    fun `words match whole, so heart is not art`() {
        assertEquals(emptyList<String>(), Genres.classify(listOf("Heartbreak")))
    }
}
