package com.preform.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPreferencesTest {

    @Test
    fun `a book override wins over the global setting`() {
        val global = ReadingPreferences(theme = ReadingTheme.LIGHT, fontSize = 1.2)
        val book = ReadingPreferences(theme = ReadingTheme.BLACK)

        val effective = ReadingPreferences.resolve(global, book)

        assertEquals(ReadingTheme.BLACK, effective.theme)
        assertEquals(1.2, effective.fontSize!!, 0.0)
    }

    @Test
    fun `unset fields fall through to the defaults`() {
        val effective = ReadingPreferences.resolve(ReadingPreferences(), ReadingPreferences())

        assertEquals(ReadingPreferences.DEFAULTS, effective)
    }

    @Test
    fun `global settings apply when the book has none`() {
        val global = ReadingPreferences(mode = ReadingMode.SCROLL)

        assertEquals(ReadingMode.SCROLL, ReadingPreferences.resolve(global, ReadingPreferences()).mode)
    }

    @Test
    fun `round trips through JSON`() {
        val prefs = ReadingPreferences(mode = ReadingMode.AUTO_SCROLL, textColor = 0xFF1E1A16.toInt(), lineHeight = 1.5)

        assertEquals(prefs, ReadingPreferences.fromJson(prefs.toJson()))
    }

    @Test
    fun `corrupt or missing JSON reads as nothing set`() {
        assertTrue(ReadingPreferences.fromJson("{not json").isEmpty)
        assertTrue(ReadingPreferences.fromJson(null).isEmpty)
    }

    @Test
    fun `unknown keys from a newer version are ignored`() {
        val prefs = ReadingPreferences.fromJson("""{"theme":"SEPIA","someFutureSetting":3}""")

        assertEquals(ReadingTheme.SEPIA, prefs.theme)
    }
}
