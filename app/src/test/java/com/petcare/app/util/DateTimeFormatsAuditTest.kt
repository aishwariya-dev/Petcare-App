package com.petcare.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class DateTimeFormatsAuditTest {

    private val originalLocale = Locale.getDefault()
    private val originalDisplayLocale = Locale.getDefault(Locale.Category.DISPLAY)
    private val originalFormatLocale = Locale.getDefault(Locale.Category.FORMAT)

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
        Locale.setDefault(Locale.Category.DISPLAY, originalDisplayLocale)
        Locale.setDefault(Locale.Category.FORMAT, originalFormatLocale)
    }

    @Test
    fun `time formatting covers minute-of-day boundaries`() {
        Locale.setDefault(Locale.US)
        assertEquals("12:00 AM", DateTimeFormats.time(0))
        assertEquals("12:01 AM", DateTimeFormats.time(1))
        assertEquals("12:00 PM", DateTimeFormats.time(12 * 60))
        assertEquals("11:59 PM", DateTimeFormats.time(23 * 60 + 59))
        assertEquals("11:59 PM", DateTimeFormats.time(LocalTime.of(23, 59, 59)))
    }

    @Test
    fun `invalid minute-of-day values are rejected rather than wrapped`() {
        org.junit.Assert.assertThrows(DateTimeException::class.java) {
            DateTimeFormats.time(-1)
        }
        org.junit.Assert.assertThrows(DateTimeException::class.java) {
            DateTimeFormats.time(24 * 60)
        }
    }

    @Test
    fun `date formats remain meaningful at month leap and year boundaries`() {
        Locale.setDefault(Locale.US)

        assertEquals("1 Jan", DateTimeFormats.shortDate(LocalDate.of(2024, 1, 1)))
        assertEquals(
            "Thursday, 29 February",
            DateTimeFormats.longDate(LocalDate.of(2024, 2, 29))
        )
        assertEquals(
            "31 December 2024",
            DateTimeFormats.fullDate(LocalDate.of(2024, 12, 31))
        )
    }

    @Test
    fun `relative date labels switch exactly at their documented horizons`() {
        Locale.setDefault(Locale.US)
        val today = LocalDate.of(2024, 12, 31)

        assertEquals("Today", DateTimeFormats.relative(today, today))
        assertEquals("Tomorrow", DateTimeFormats.relative(today.plusDays(1), today))
        assertEquals("Thursday", DateTimeFormats.relative(today.plusDays(2), today))
        assertEquals("Monday", DateTimeFormats.relative(today.plusDays(6), today))
        assertEquals("7 January", DateTimeFormats.relative(today.plusDays(7), today))
        assertEquals("30 December", DateTimeFormats.relative(today.minusDays(1), today))
    }

    @Test
    fun `greeting boundaries are stable at every transition hour`() {
        val cases = listOf(
            0 to "Hello",
            4 to "Hello",
            5 to "Good morning",
            11 to "Good morning",
            12 to "Good afternoon",
            16 to "Good afternoon",
            17 to "Good evening",
            21 to "Good evening",
            22 to "Hello",
            23 to "Hello"
        )

        cases.forEach { (hour, expected) ->
            assertEquals(expected, DateTimeFormats.greeting(LocalTime.of(hour, 0)))
        }
    }
}