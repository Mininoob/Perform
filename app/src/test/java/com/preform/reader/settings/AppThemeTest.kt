package com.preform.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    @Test
    fun `the old light, dark and system choices carry over`() {
        assertEquals(AppTheme.DEFAULT, legacyAppTheme("SYSTEM"))
        assertEquals(AppTheme.DEFAULT, legacyAppTheme("DARK"))
        assertEquals(AppTheme(ThemeBase.PAPER, ThemeAccent.BLUE), legacyAppTheme("LIGHT"))
    }

    @Test
    fun `noir stays black and white`() {
        assertEquals(AppTheme(ThemeBase.NOIR, ThemeAccent.MONO), legacyAppTheme("NOIR"))
    }

    @Test
    fun `named color themes become their nearest base wearing their color`() {
        assertEquals(AppTheme(ThemeBase.INK, ThemeAccent.RED), legacyAppTheme("CRIMSON"))
        assertEquals(AppTheme(ThemeBase.PAPER, ThemeAccent.VIOLET), legacyAppTheme("LAVENDER"))
        assertEquals(AppTheme(ThemeBase.SLATE, ThemeAccent.TEAL), legacyAppTheme("SLATE"))
    }

    @Test
    fun `never set, removed or unknown themes fall back to the default`() {
        assertEquals(AppTheme.DEFAULT, legacyAppTheme(null))
        assertEquals(AppTheme(ThemeBase.INK, ThemeAccent.RED), legacyAppTheme("EMBER"))
        assertEquals(AppTheme.DEFAULT, legacyAppTheme("SOMETHING_NEWER"))
    }

    @Test
    fun `three light bases and three dark`() {
        assertEquals(3, ThemeBase.entries.count { !it.dark })
        assertEquals(3, ThemeBase.entries.count { it.dark })
        assertTrue(ThemeBase.entries.all { themeBaseNamed(it.name) == it })
    }
}
