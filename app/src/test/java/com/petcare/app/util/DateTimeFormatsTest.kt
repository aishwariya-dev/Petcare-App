package com.petcare.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateTimeFormatsTest {
    private val originalLocale = Locale.getDefault()
    private val originalDisplayLocale = Locale.getDefault(Locale.Category.DISPLAY)
    private val originalFormatLocale = Locale.getDefault(Locale.Category.FORMAT)
    private val date = LocalDate.of(2026, 9, 16)

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
        Locale.setDefault(Locale.Category.DISPLAY, originalDisplayLocale)
        Locale.setDefault(Locale.Category.FORMAT, originalFormatLocale)
    }

    @Test
    fun `date text follows locale changes without restarting the app`() {
        Locale.setDefault(Locale.US)
        assertEquals(expectedDates(Locale.US), formattedDates())

        Locale.setDefault(Locale.FRANCE)
        assertEquals(expectedDates(Locale.FRANCE), formattedDates())

        Locale.setDefault(Locale.GERMANY)
        assertEquals(expectedDates(Locale.GERMANY), formattedDates())
    }

    @Test
    fun `time always shows a 12-hour clock with AM PM in every locale`() {
        Locale.setDefault(Locale.US)
        assertEquals("9:05 AM", DateTimeFormats.time(9 * 60 + 5))
        assertEquals("12:30 PM", DateTimeFormats.time(LocalTime.of(12, 30)))

        Locale.setDefault(Locale.FRANCE)
        assertEquals("Today", DateTimeFormats.relative(date, date))
        assertEquals("Tomorrow", DateTimeFormats.relative(date.plusDays(1), date))
        // A 24-hour locale still gets an explicit marker, separated by a plain ASCII space.
        assertEquals("9:05 AM", DateTimeFormats.time(9 * 60 + 5))
        assertEquals("12:30 PM", DateTimeFormats.time(LocalTime.of(12, 30)))
        assertEquals("12:00 AM", DateTimeFormats.time(0))
        assertEquals("11:59 PM", DateTimeFormats.time(23 * 60 + 59))
        assertEquals(
            date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)),
            DateTimeFormats.relative(date, date.minusDays(10))
        )
    }

    private fun formattedDates() = listOf(
        DateTimeFormats.shortDate(date),
        DateTimeFormats.longDate(date),
        DateTimeFormats.fullDate(date),
        DateTimeFormats.relative(date, date.minusDays(2))
    )

    private fun expectedDates(locale: Locale) = listOf(
        "d MMM", "EEEE, d MMMM", "d MMMM yyyy", "EEEE"
    ).map { pattern -> date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
}