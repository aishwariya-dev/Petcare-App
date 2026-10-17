package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.screens.tasks.TaskDetailData
import com.petcare.app.ui.screens.tasks.TaskDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskDetailCompletionConsistencyTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { environment.auth.logout() }
    }

    @After
    fun tearDown() {
        runBlocking { environment.auth.logout() }
        environment.close()
    }

    @Test
    fun `future one-off detail cannot mark completion and retains its occurrence`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register(
            "Detail owner",
            "detail-future-completion@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Detail pet", species = Species.DOG.name)
        )
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Future detail one-off",
                category = TaskCategory.MEDICATION.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = LocalDate.now().plusDays(1).toEpochDay(),
                timeMinutes = 8 * 60
            )
        )
        val viewModel = TaskDetailViewModel(
            environment.tasks,
            environment.pets,
            SavedStateHandle(mapOf(Routes.ARG_TASK_ID to taskId))
        )
        val collector: Job = launch { viewModel.state.collect { } }
        try {
            awaitCondition(description = "future detail state") {
                (viewModel.state.value as? UiState.Success<TaskDetailData?>)?.data?.task?.id == taskId
            }
            val detail = (viewModel.state.value as UiState.Success<TaskDetailData?>).data!!
            assertFalse(detail.occursToday)
            // A mutable/forged caller snapshot says "today", but the detail
            // state still represents the stored future schedule.
            val forgedSnapshot = detail.task.copy(startDateEpochDay = LocalDate.now().toEpochDay())
            var done = false
            var rejected = false
            var rejectionMessage: String? = null
            viewModel.setCompleted(
                forgedSnapshot,
                completed = true,
                onError = {
                    rejected = true
                    rejectionMessage = it
                },
                onDone = { done = true }
            )
            awaitCondition(description = "future detail completion rejection") { rejected || done }
            assertFalse(done)
            assertTrue(rejected)
            assertEquals("This task is not scheduled today.", rejectionMessage)
            assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
        } finally {
            collector.cancelAndJoin()
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}