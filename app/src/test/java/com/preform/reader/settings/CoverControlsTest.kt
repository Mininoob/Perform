package com.preform.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverControlsTest {

    @Test
    fun `back swipes switched off stay off`() {
        assertEquals(CoverControls.OFF, coverControlsFrom(saved = null, legacySwipeEnabled = false))
    }

    @Test
    fun `back swipes left on, or never touched, gain taps`() {
        assertEquals(CoverControls.SWIPE_AND_TAP, coverControlsFrom(saved = null, legacySwipeEnabled = true))
        assertEquals(CoverControls.SWIPE_AND_TAP, coverControlsFrom(saved = null, legacySwipeEnabled = null))
    }

    @Test
    fun `a saved choice wins over the old switch`() {
        CoverControls.entries.forEach {
            assertEquals(it, coverControlsFrom(saved = it.name, legacySwipeEnabled = false))
        }
    }
}
