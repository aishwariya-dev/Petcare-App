package com.petcare.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeFormats {

    // Times are always shown as 12-hour with an explicit AM/PM marker so a
    // reminder time is unambiguous regardless of the phone's clock setting.
    // The ASCII space keeps the marker inside the cheap GSM SMS alphabet
    // (a locale-provided narrow no-break space would force UCS-2).
    private val shortTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    // The process can outlive a language change, so do not cache the default locale.
    private val shortDate: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val longDate: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val fullDate: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    private val dayName: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())

    fun time(minutesOfDay: Int): String =
        shortTime.format(LocalTime.ofSecondOfDay(minutesOfDay * 60L))

    fun time(time: LocalTime): String = shortTime.format(time)

    fun shortDate(date: LocalDate): String = shortDate.format(date)

    fun longDate(date: LocalDate): String = longDate.format(date)

    fun fullDate(date: LocalDate): String = fullDate.format(date)

    /** "Today", "Tomorrow", a weekday name within the week, otherwise "25 September". */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date.isAfter(today) && date.isBefore(today.plusDays(7)) -> dayName.format(date)
        else -> DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()).format(date)
    }

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}
