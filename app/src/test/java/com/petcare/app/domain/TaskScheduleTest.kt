package com.petcare.app.domain

import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskScheduleTest {

    // Tuesday 15 September 2026.
    private val today: LocalDate = LocalDate.of(2026, 9, 15)

    private fun task(
        frequency: Frequency,
        start: LocalDate,
        completed: LocalDate? = null,
        time: Int = 8 * 60,
        name: String = "Task",
        reminder: Boolean = false
    ) = CareTask(
        id = 1,
        petId = 1,
        name = name,
        category = TaskCategory.FEEDING.name,
        frequency = frequency.name,
        startDateEpochDay = start.toEpochDay(),
        timeMinutes = time,
        reminderEnabled = reminder,
        lastCompletedEpochDay = completed?.toEpochDay()
    )

    @Test
    fun `daily task occurs from its start date onwards`() {
        val t = task(Frequency.DAILY, today.minusDays(3))
        assertTrue(TaskSchedule.occursOn(t, today))
        assertTrue(TaskSchedule.occursOn(t, today.plusDays(30)))
        assertFalse(TaskSchedule.occursOn(t, today.minusDays(4)))
    }

    @Test
    fun `weekly task occurs only on the same weekday`() {
        val t = task(Frequency.WEEKLY, today) // Tuesday
        assertTrue(TaskSchedule.occursOn(t, today))
        assertFalse(TaskSchedule.occursOn(t, today.plusDays(1)))
        assertTrue(TaskSchedule.occursOn(t, today.plusDays(7)))
    }

    @Test
    fun `one-off task stays on the list until completed`() {
        val pending = task(Frequency.ONCE, today.minusDays(2))
        assertTrue(TaskSchedule.occursOn(pending, today))
        assertTrue(TaskSchedule.isOverdue(pending, today))

        val done = task(Frequency.ONCE, today.minusDays(2), completed = today.minusDays(2))
        assertFalse(TaskSchedule.occursOn(done, today))
        assertFalse(TaskSchedule.isOverdue(done, today))
    }

    @Test
    fun `completed one-off task remains checked on its due date for undo`() {
        val done = task(Frequency.ONCE, today, completed = today)

        assertTrue(TaskSchedule.occursOn(done, today))
        assertTrue(TaskSchedule.isCompletedOn(done, today))
        assertFalse(TaskSchedule.isCompletedOn(done, today.plusDays(1)))
        assertNull(TaskSchedule.nextOccurrenceAfter(done, today.minusDays(1)))
    }

    @Test
    fun `completed overdue one-off is excluded after it is completed`() {
        val done = task(Frequency.ONCE, today.minusDays(1), completed = today)

        assertFalse(TaskSchedule.occursOn(done, today))
        assertFalse(TaskSchedule.isOverdue(done, today))
    }

    @Test
    fun `one-off task in the future is not overdue and not shown today`() {
        val t = task(Frequency.ONCE, today.plusDays(1))
        assertFalse(TaskSchedule.occursOn(t, today))
        assertFalse(TaskSchedule.isOverdue(t, today))
    }

    @Test
    fun `recurring completion only counts for the day it was recorded`() {
        val t = task(Frequency.DAILY, today.minusDays(5), completed = today.minusDays(1))
        assertFalse(TaskSchedule.isCompletedOn(t, today))
        assertTrue(TaskSchedule.isCompletedOn(t, today.minusDays(1)))
    }

    @Test
    fun `completion before a recurrence starts or on a non-occurring day is ignored`() {
        val weekly = task(Frequency.WEEKLY, today, completed = today.plusDays(1))
        val monthly = task(
            Frequency.MONTHLY,
            LocalDate.of(2026, 1, 31),
            completed = LocalDate.of(2026, 2, 27)
        )

        assertFalse(TaskSchedule.isCompletedOn(weekly, today.plusDays(1)))
        assertFalse(TaskSchedule.isCompletedOn(monthly, LocalDate.of(2026, 2, 27)))
        assertFalse(
            TaskSchedule.isCompletedOn(
                task(Frequency.DAILY, today.plusDays(1), completed = today),
                today
            )
        )
    }

    @Test
    fun `next occurrence for weekly task lands on the correct weekday`() {
        val start = today.minusDays(8) // a Monday
        val t = task(Frequency.WEEKLY, start)
        val next = TaskSchedule.nextOccurrenceAfter(t, today)
        assertEquals(DayOfWeek.MONDAY, next?.dayOfWeek)
        assertEquals(today.plusDays(6), next)
    }

    @Test
    fun `invalid persisted start date is ignored by schedule calculations`() {
        val invalid = task(Frequency.DAILY, today).copy(startDateEpochDay = Long.MAX_VALUE)
        val now = ZonedDateTime.of(today.atTime(12, 0), ZoneId.of("UTC"))

        assertFalse(TaskSchedule.occursOn(invalid, today))
        assertFalse(TaskSchedule.isCompletedOn(invalid, today))
        assertFalse(TaskSchedule.isOverdue(invalid, today))
        assertNull(TaskSchedule.nextOccurrenceAfter(invalid, today))
        assertNull(TaskSchedule.nextOccurrenceOnOrAfter(invalid, today))
        assertNull(TaskSchedule.nextReminderAt(invalid.copy(reminderEnabled = true), now))
    }

    @Test
    fun `daily tasks have no upcoming occurrence and past one-offs return null`() {
        assertNull(TaskSchedule.nextOccurrenceAfter(task(Frequency.DAILY, today), today))
        assertNull(TaskSchedule.nextOccurrenceAfter(task(Frequency.ONCE, today), today))
        assertEquals(today.plusDays(3), TaskSchedule.nextOccurrenceAfter(task(Frequency.ONCE, today.plusDays(3)), today))
    }

    @Test
    fun `tasksOn sorts by time then name`() {
        val a = task(Frequency.DAILY, today, time = 9 * 60, name = "Walk")
        val b = task(Frequency.DAILY, today, time = 7 * 60, name = "Breakfast")
        val c = task(Frequency.DAILY, today, time = 9 * 60, name = "Medication")
        val skipped = task(Frequency.WEEKLY, today.plusDays(1))
        assertEquals(listOf("Breakfast", "Medication", "Walk"), TaskSchedule.tasksOn(listOf(a, b, c, skipped), today).map { it.name })
    }

    @Test
    fun `monthly task clamps day thirty one in short months`() {
        val start = LocalDate.of(2026, 1, 31)
        val t = task(Frequency.MONTHLY, start, reminder = true)

        assertTrue(TaskSchedule.occursOn(t, LocalDate.of(2026, 2, 28)))
        assertFalse(TaskSchedule.occursOn(t, LocalDate.of(2026, 2, 27)))
        assertTrue(TaskSchedule.occursOn(t, LocalDate.of(2026, 3, 31)))
        assertEquals(
            LocalDate.of(2026, 2, 28),
            TaskSchedule.nextOccurrenceAfter(t, start)
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            TaskSchedule.nextOccurrenceAfter(t, LocalDate.of(2026, 2, 28))
        )
    }

    @Test
    fun `next reminder preserves the configured minute and skips passed daily time`() {
        val t = task(
            Frequency.DAILY,
            LocalDate.of(2026, 9, 1),
            time = 14 * 60 + 35,
            reminder = true
        )
        val zone = ZoneId.of("UTC")

        assertEquals(
            ZonedDateTime.of(2026, 9, 15, 14, 35, 0, 0, zone),
            TaskSchedule.nextReminderAt(
                t,
                ZonedDateTime.of(2026, 9, 15, 14, 34, 0, 0, zone)
            )
        )
        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 14, 35, 0, 0, zone),
            TaskSchedule.nextReminderAt(
                t,
                ZonedDateTime.of(2026, 9, 15, 14, 35, 0, 0, zone)
            )
        )
    }

    @Test
    fun `next reminder handles a spring DST gap without an invalid instant`() {
        val t = task(
            Frequency.DAILY,
            LocalDate.of(2026, 3, 1),
            time = 2 * 60 + 30,
            reminder = true
        )
        val zone = ZoneId.of("America/New_York")

        val next = TaskSchedule.nextReminderAt(
            t,
            ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, zone)
        )

        // 02:30 does not exist on the spring transition; java.time resolves
        // it to the first valid local time after the gap.
        assertEquals(LocalDate.of(2026, 3, 8), next?.toLocalDate())
        assertEquals(LocalTime.of(3, 30), next?.toLocalTime())
        assertEquals(zone, next?.zone)
    }
}
