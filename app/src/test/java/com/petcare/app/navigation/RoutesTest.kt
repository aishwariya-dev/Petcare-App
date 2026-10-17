package com.petcare.app.navigation

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoutesTest {
    @Test
    fun taskRoutesShareTheTasksTopLevelIdentity() {
        assertEquals(Routes.TASKS, Routes.canonicalTopLevel(Routes.TASKS_WITH_TAB))
        assertEquals(Routes.TASKS, Routes.canonicalTopLevel(Routes.tasks("upcoming")))
    }

    @Test
    fun nestedRoutesDoNotShowTheBottomBar() {
        assertEquals(null, Routes.canonicalTopLevel(Routes.taskDetail(42L)))
        assertEquals(Routes.HOME, Routes.canonicalTopLevel(Routes.HOME))
    }

    @Test
    fun delegationPreviewEscapesPhoneRouteArguments() {
        val route = Routes.delegatePreview(42L, "1111", "+44 7700/900000")

        assertTrue(route.contains("%2B44%207700%2F900000"))
    }

    @Test
    fun topLevelTabsReportTheirVisualMovementDirection() {
        assertEquals(1, topLevelTabDirection(Routes.HOME, Routes.SETTINGS))
        assertEquals(-1, topLevelTabDirection(Routes.SETTINGS, Routes.PETS))
        assertEquals(0, topLevelTabDirection(Routes.TASKS, Routes.TASKS_WITH_TAB))
        assertEquals(0, topLevelTabDirection(Routes.taskDetail(4L), Routes.PETS))
    }
}
