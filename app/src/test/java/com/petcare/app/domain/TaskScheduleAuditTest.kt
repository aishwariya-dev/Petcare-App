package com.petcare.app.domain

import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TaskScheduleAuditTest {

    private val tuesday = LocalDate.of(2026, 9, 15)

    private fun task(
        frequency: String,
        start: LocalDate,
        completed: LocalDate? = null,
        timeMinutes: Int = 8 * 60,
        reminderEnabled: Boolean = false,
        smsReminderEnabled: Boolean = false
    ) = CareTask(
        id = 7,
        petId = 3,
        name = "Audit task",
        category = TaskCategory.FEEDING.name,
        frequency = frequency,
        startDateEpochDay = start.toEpochDay(),
        timeMinutes = timeMinutes,
        reminderEnabled = reminderEnabled,
        smsReminderEnabled = smsReminderEnabled,
        lastCompletedEpochDay = completed?.toEpochDay()
    )

    @Test
    fun `each frequency has a distinct start and recurrence boundary`() {
        val once = task(Frequency.ONCE.name, tuesday)
        assertFalse(TaskSchedule.occursOn(once, tuesday.minusDays(1)))
        assertTrue(TaskSchedule.occursOn(once, tuesday))
        assertTrue(TaskSchedule.occursOn(once, tuesday.plusDays(1)))

        val daily = task(Frequency.DAILY.name, tuesday)
        assertFalse(TaskSchedule.occursOn(daily, tuesday.minusDays(1)))
        assertTrue(TaskSchedule.occursOn(daily, tuesday.plusDays(40)))

        val weekly = task(Frequency.WEEKLY.name, tuesday)
        assertTrue(TaskSchedule.occursOn(weekly, tuesday))
        assertFalse(TaskSchedule.occursOn(weekly, tuesday.plusDays(1)))
        assertTrue(TaskSchedule.occursOn(weekly, tuesday.plusDays(7)))

        val monthly = task(Frequency.MONTHLY.name, LocalDate.of(2024, 1, 31))
        assertFalse(TaskSchedule.occursOn(monthly, LocalDate.of(2024, 2, 28)))
        assertTrue(TaskSchedule.occursOn(monthly, LocalDate.of(2024, 2, 29)))
        assertTrue(TaskSchedule.occursOn(monthly, LocalDate.of(2024, 3, 31)))
    }

    @Test
    fun `next occurrence on or after handles future starts and same day occurrences`() {
        assertEquals(
            tuesday.plusDays(2),
            TaskSchedule.nextOccurrenceOnOrAfter(
                task(Frequency.ONCE.name, tuesday.plusDays(2)),
                tuesday
            )
        )
        assertEquals(
            tuesday,
            TaskSchedule.nextOccurrenceOnOrAfter(task(Frequency.ONCE.name, tuesday), tuesday)
        )
        assertNull(
            TaskSchedule.nextOccurrenceOnOrAfter(
                task(Frequency.ONCE.name, tuesday, completed = tuesday),
                tuesday
            )
        )

        assertEquals(
            tuesday,
            TaskSchedule.nextOccurrenceOnOrAfter(
                task(Frequency.DAILY.name, tuesday.minusDays(2)),
                tuesday
            )
        )
        assertEquals(
            tuesday.plusDays(7),
            TaskSchedule.nextOccurrenceOnOrAfter(
                task(Frequency.WEEKLY.name, tuesday),
                tuesday.plusDays(1)
            )
        )
        assertEquals(
            LocalDate.of(2026, 10, 31),
            TaskSchedule.nextOccurrenceOnOrAfter(
                task(Frequency.MONTHLY.name, LocalDate.of(2026, 1, 31)),
                LocalDate.of(2026, 10, 1)
            )
        )
    }

    @Test
    fun `monthly recurrence crosses leap year and calendar year boundaries`() {
        val leapStart = LocalDate.of(2024, 1, 31)
        assertNull(TaskSchedule.monthlyOccurrence(leapStart, java.time.YearMonth.of(2023, 12)))
        assertEquals(
            LocalDate.of(2024, 2, 29),
            TaskSchedule.monthlyOccurrence(leapStart, java.time.YearMonth.of(2024, 2))
        )
        assertEquals(
            LocalDate.of(2025, 1, 31),
            TaskSchedule.nextOccurrenceAfter(
                task(Frequency.MONTHLY.name, leapStart),
                LocalDate.of(2024, 12, 31)
            )
        )

        val yearEnd = task(Frequency.MONTHLY.name, LocalDate.of(2026, 12, 31))
        assertEquals(
            LocalDate.of(2027, 1, 31),
            TaskSchedule.nextOccurrenceAfter(yearEnd, LocalDate.of(2026, 12, 31))
        )
    }

    @Test
    fun `completion and overdue state is evaluated against each occurrence`() {
        val once = task(Frequency.ONCE.name, tuesday.minusDays(2))
        assertTrue(TaskSchedule.isOverdue(once, tuesday))
        assertFalse(TaskSchedule.isCompletedOn(once, tuesday))
        val completedOnce = once.copy(lastCompletedEpochDay = tuesday.minusDays(2).toEpochDay())
        assertFalse(TaskSchedule.isOverdue(completedOnce, tuesday))
        assertFalse(TaskSchedule.occursOn(completedOnce, tuesday))

        val daily = task(Frequency.DAILY.name, tuesday.minusDays(3), completed = tuesday)
        assertTrue(TaskSchedule.isCompletedOn(daily, tuesday))
        assertFalse(TaskSchedule.isCompletedOn(daily, tuesday.minusDays(1)))

        val weekly = task(Frequency.WEEKLY.name, tuesday, completed = tuesday)
        assertTrue(TaskSchedule.isCompletedOn(weekly, tuesday))
        assertFalse(TaskSchedule.isCompletedOn(weekly, tuesday.plusDays(1)))

        val monthly = task(
            Frequency.MONTHLY.name,
            LocalDate.of(2024, 1, 31),
            completed = LocalDate.of(2024, 2, 29)
        )
        assertTrue(TaskSchedule.isCompletedOn(monthly, LocalDate.of(2024, 2, 29)))
        assertFalse(TaskSchedule.isCompletedOn(monthly, LocalDate.of(2024, 2, 28)))
    }

    @Test
    fun `next reminder honours disabled completed and exact-time states`() {
        val zone = ZoneId.of("UTC")
        val daily = task(
            Frequency.DAILY.name,
            tuesday.minusDays(20),
            timeMinutes = 14 * 60 + 35,
            reminderEnabled = true
        )
        assertNull(
            TaskSchedule.nextReminderAt(
                daily.copy(reminderEnabled = false),
                ZonedDateTime.of(2026, 9, 15, 13, 0, 0, 0, zone)
            )
        )
        assertEquals(
            ZonedDateTime.of(2026, 9, 15, 14, 35, 0, 0, zone),
            TaskSchedule.nextReminderAt(
                daily.copy(reminderEnabled = false, smsReminderEnabled = true),
                ZonedDateTime.of(2026, 9, 15, 14, 34, 59, 0, zone)
            )
        )
        assertEquals(
            ZonedDateTime.of(2026, 9, 15, 14, 35, 0, 0, zone),
            TaskSchedule.nextReminderAt(
                daily,
                ZonedDateTime.of(2026, 9, 15, 14, 34, 59, 0, zone)
            )
        )
        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 14, 35, 0, 0, zone),
            TaskSchedule.nextReminderAt(
                daily.copy(lastCompletedEpochDay = tuesday.toEpochDay()),
                ZonedDateTime.of(2026, 9, 15, 14, 34, 59, 0, zone)
            )
        )

        val monthly = task(
            Frequency.MONTHLY.name,
            LocalDate.of(2026, 1, 31),
            timeMinutes = 6 * 60,
            reminderEnabled = true
        )
        assertEquals(
            ZonedDateTime.of(2026, 3, 31, 6, 0, 0, 0, zone),
            TaskSchedule.nextReminderAt(
                monthly,
                ZonedDateTime.of(2026, 2, 28, 6, 1, 0, 0, zone)
            )
        )
    }

    @Test
    fun `next reminder resolves an autumn DST overlap in the configured zone`() {
        val zone = ZoneId.of("America/New_York")
        val task = task(
            Frequency.DAILY.name,
            LocalDate.of(2026, 10, 1),
            timeMinutes = 1 * 60 + 30,
            reminderEnabled = true
        )

        val next = TaskSchedule.nextReminderAt(
            task,
            ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, zone)
        )

        assertEquals(LocalDate.of(2026, 11, 1), next?.toLocalDate())
        assertEquals(LocalTime.of(1, 30), next?.toLocalTime())
        assertEquals(ZoneOffset.ofHours(-4), next?.offset)
    }

    @Test
    fun `malformed persisted dates frequencies and times do not escape scheduling`() {
        val now = ZonedDateTime.of(tuesday.atTime(12, 0), ZoneId.of("UTC"))
        val invalidDates = listOf(Long.MIN_VALUE, Long.MAX_VALUE)
        invalidDates.forEach { epochDay ->
            val invalid = task(Frequency.DAILY.name, tuesday)
                .copy(startDateEpochDay = epochDay, reminderEnabled = true)
            assertFalse(TaskSchedule.occursOn(invalid, tuesday))
            assertNull(TaskSchedule.nextOccurrenceOnOrAfter(invalid, tuesday))
            assertNull(TaskSchedule.nextReminderAt(invalid, now))
        }

        val legacy = task("old-frequency", tuesday)
        assertTrue(TaskSchedule.occursOn(legacy, tuesday))
        assertNull(TaskSchedule.nextOccurrenceAfter(legacy, tuesday))

        listOf(-1, 24 * 60, Int.MAX_VALUE).forEach { invalidMinutes ->
            val invalidTime = task(
                Frequency.DAILY.name,
                tuesday,
                timeMinutes = invalidMinutes,
                reminderEnabled = true
            )
            assertNull(TaskSchedule.nextReminderAt(invalidTime, now))
        }
    }

    @Test
    fun `date maximum has no overflow when asking for a later occurrence`() {
        val start = LocalDate.MAX.minusDays(1)
        val weekly = task(Frequency.WEEKLY.name, start)
        assertNull(TaskSchedule.nextOccurrenceAfter(weekly, LocalDate.MAX))

        val monthly = task(Frequency.MONTHLY.name, LocalDate.MAX)
        assertNull(TaskSchedule.nextOccurrenceAfter(monthly, LocalDate.MAX))
    }
}