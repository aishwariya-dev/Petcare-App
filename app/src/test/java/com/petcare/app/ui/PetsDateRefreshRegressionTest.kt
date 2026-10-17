package com.petcare.app.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.screens.pets.PetsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PetsDateRefreshRegressionTest {
    @Test
    fun `today counts refresh when date changes without a database write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val environment = TestEnvironment(ApplicationProvider.getApplicationContext())
        val day = LocalDate.of(2026, 9, 17)
        val dates = MutableStateFlow(day)
        val owner = environment.auth.register("Date", "date@example.com", "secret1").getOrThrow()
        val pet = environment.pets.save(
            Pet(ownerId = owner.id, name = "Milo", species = Species.DOG.name)
        )
        environment.tasks.save(
            CareTask(petId = pet, name = "Vet visit", category = TaskCategory.HEALTH_CHECK.name,
                frequency = Frequency.ONCE.name, startDateEpochDay = day.plusDays(1).toEpochDay(),
                timeMinutes = 600)
        )
        val vm = PetsViewModel(environment.auth, environment.pets, environment.tasks, dates)
        val collector = launch { vm.state.collect { } }
        try {
            awaitCondition(description = "today's pet count") {
                (vm.state.value as? UiState.Success)?.data?.singleOrNull()?.tasksToday == 0
            }
            dates.value = day.plusDays(1)
            awaitCondition(description = "next day's pet count") {
                (vm.state.value as? UiState.Success)?.data?.singleOrNull()?.tasksToday == 1
            }
            assertEquals(0, (vm.state.value as UiState.Success).data.single().completedToday)
        } finally {
            collector.cancelAndJoin()
            vm.viewModelScope.cancel()
            testScheduler.runCurrent()
            environment.auth.logout()
            environment.close()
            Dispatchers.resetMain()
        }
    }
}