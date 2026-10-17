package com.petcare.app.navigation

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoutesAuditTest {

    @Test
    fun `route builders preserve ids and distinguish add from edit destinations`() {
        assertEquals("expense_form", Routes.addExpense())
        assertEquals("expense_form?expenseId=0", Routes.editExpense(0))
        assertEquals("expense_form?expenseId=987654321", Routes.editExpense(987654321))

        assertEquals("pet/0", Routes.petDetail(0))
        assertEquals("pet_form", Routes.addPet())
        assertEquals("pet_form?petId=42", Routes.editPet(42))

        assertEquals("task/17", Routes.taskDetail(17))
        assertEquals("task_form", Routes.addTask())
        assertEquals("task_form?petId=17", Routes.addTask(17))
        assertEquals("task_form?start=20000", Routes.addTask(startEpochDay = 20000L))
        assertEquals("task_form?petId=17&start=20000", Routes.addTask(17, 20000L))
        assertEquals("task_form?taskId=17", Routes.editTask(17))
        assertEquals("tasks?tab=today", Routes.tasks())
        assertEquals("tasks?tab=upcoming", Routes.tasks("upcoming"))
    }

    @Test
    fun `delegation routes carry flags and encode phone path segments`() {
        assertEquals("delegate/42", Routes.delegate(42))
        assertEquals(
            "delegate_preview/42/1010/%2B44%207700%2F900000",
            Routes.delegatePreview(42, "1010", "+44 7700/900000")
        )
        assertEquals(
            "delegate_preview/7/0000/5551234567",
            Routes.delegatePreview(7, "0000", "5551234567")
        )
    }

    @Test
    fun `canonical top-level identity only exposes bottom-bar destinations`() {
        val expected = mapOf(
            null to null,
            "home" to "home",
            "pets" to "pets",
            "tasks?tab=today" to "tasks",
            "tasks?tab=history" to "tasks",
            "settings?tab=profile" to "settings",
            "upcoming" to null,
            "pet/42" to null,
            "unknown?tab=today" to null,
            "?tab=today" to null
        )

        expected.forEach { (route, canonical) ->
            if (canonical == null) {
                assertNull(Routes.canonicalTopLevel(route))
            } else {
                assertEquals(canonical, Routes.canonicalTopLevel(route))
            }
        }
    }

    @Test
    fun `route builders do not normalize caller supplied tab or flag values`() {
        assertEquals("tasks?tab=care%20plan", Routes.tasks("care%20plan"))
        assertEquals(
            "delegate_preview/3/1x0x/555",
            Routes.delegatePreview(3, "1x0x", "555")
        )
    }
}