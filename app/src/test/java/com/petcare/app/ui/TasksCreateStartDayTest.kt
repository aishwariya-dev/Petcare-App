package com.petcare.app.ui

import com.petcare.app.ui.screens.tasks.TaskTab
import com.petcare.app.ui.screens.tasks.createStartDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TasksCreateStartDayTest {
    @Test
    fun `Today keeps the form default and Upcoming starts tomorrow`() {
        val today = LocalDate.of(2026, 9, 23)
        assertNull(createStartDay(TaskTab.TODAY, today))
        assertEquals(today.plusDays(1).toEpochDay(), createStartDay(TaskTab.UPCOMING, today))
    }
}
