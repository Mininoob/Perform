package com.perform.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ThemeScheduleTest {

    private val schedule = ThemeSchedule.DEFAULT.copy(enabled = true) // day 7:00, night 19:00

    @Test
    fun `day theme between morning and evening`() {
        assertEquals(schedule.day, schedule.baseAt(LocalTime.of(12, 0)))
    }

    @Test
    fun `night theme overnight`() {
        assertEquals(schedule.night, schedule.baseAt(LocalTime.of(23, 30)))
        assertEquals(schedule.night, schedule.baseAt(LocalTime.of(3, 0)))
    }

    @Test
    fun `each theme takes over exactly at its start time`() {
        assertEquals(schedule.day, schedule.baseAt(LocalTime.of(7, 0)))
        assertEquals(schedule.night, schedule.baseAt(LocalTime.of(6, 59)))
        assertEquals(schedule.night, schedule.baseAt(LocalTime.of(19, 0)))
        assertEquals(schedule.day, schedule.baseAt(LocalTime.of(18, 59)))
    }

    @Test
    fun `a day that starts after night wraps through midnight`() {
        // A night-shift reader: "day" from 21:00 to 06:00.
        val shifted = schedule.copy(dayStartsAt = LocalTime.of(21, 0), nightStartsAt = LocalTime.of(6, 0))
        assertEquals(shifted.day, shifted.baseAt(LocalTime.of(23, 0)))
        assertEquals(shifted.day, shifted.baseAt(LocalTime.of(2, 0)))
        assertEquals(shifted.night, shifted.baseAt(LocalTime.of(12, 0)))
    }
}
