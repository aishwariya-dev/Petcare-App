package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.CareTask
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.screens.expenses.ExpensesData
import com.petcare.app.ui.screens.expenses.ExpensesViewModel
import com.petcare.app.ui.screens.pets.PetProfileData
import com.petcare.app.ui.screens.pets.PetProfileViewModel
import com.petcare.app.ui.screens.tasks.TaskDetailViewModel
import com.petcare.app.ui.screens.tasks.TaskDetailData
import com.petcare.app.ui.screens.tasks.TaskTab
import com.petcare.app.ui.screens.tasks.TasksData
import com.petcare.app.ui.screens.tasks.TasksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ListDetailViewModelTest {
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
    fun `tasks list supports tab done reset delete and undo actions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Task List", "task-list@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Milo", species = Species.DOG.name)
        )
        val task = CareTask(
            petId = petId,
            name = "Daily food",
            category = TaskCategory.FEEDING.name,
            frequency = Frequency.DAILY.name,
            startDateEpochDay = LocalDate.now().toEpochDay(),
            timeMinutes = 8 * 60
        )
        val taskId = environment.tasks.save(task)
        val savedTask = task.copy(id = taskId)
        val viewModel = TasksViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            SavedStateHandle(mapOf(Routes.ARG_TAB to "upcoming"))
        )
        val collector = launch { viewModel.state.collect { } }
        try {
            awaitCondition(description = "tasks list content") {
                val data = (viewModel.state.value as? UiState.Success<TasksData>)?.data
                data?.todayGroups?.flatMap { it.tasks }?.any { it.id == taskId } == true
            }
            assertEquals(TaskTab.UPCOMING, viewModel.tab.value)
            viewModel.selectTab(TaskTab.TODAY)
            assertEquals(TaskTab.TODAY, viewModel.tab.value)

            var done = false
            viewModel.setCompleted(savedTask, completed = true, onDone = { done = true })
            awaitCondition(description = "task completed callback") { done }
            assertEquals(
                LocalDate.now().toEpochDay(),
                environment.tasks.getTask(taskId)!!.lastCompletedEpochDay
            )

            var resetCount = -1
            viewModel.resetToday { resetCount = it }
            awaitCondition(description = "task completion reset") { resetCount == 1 }
            assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)

            var deleted = false
            viewModel.delete(savedTask, onDeleted = { deleted = true }, onError = {})
            awaitCondition(description = "task deleted callback") { deleted }
            assertNull(environment.tasks.getTask(taskId))

            var restored = false
            viewModel.restore(savedTask, onRestored = { restored = true }, onError = {})
            awaitCondition(description = "task restored callback") { restored }
            assertNotNull(environment.tasks.getTask(taskId))
        } finally {
            collector.cancelAndJoin()
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `pet profile and expense list expose delete and completion actions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Detail Owner", "detail@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Luna", species = Species.CAT.name)
        )
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Brush",
                category = TaskCategory.GROOMING.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = LocalDate.now().toEpochDay(),
                timeMinutes = 10 * 60
            )
        )
        val expenseId = environment.expenses.save(
            Expense(
                ownerId = owner.id,
                petId = petId,
                amount = 8.0,
                category = ExpenseCategory.GROOMING.name,
                description = "Brush"
            )
        )
        val profile = PetProfileViewModel(
            environment.pets,
            environment.tasks,
            SavedStateHandle(mapOf(Routes.ARG_PET_ID to petId))
        )
        val expenses = ExpensesViewModel(environment.auth, environment.expenses)
        val profileCollector = launch { profile.state.collect { } }
        val expenseCollector = launch { expenses.state.collect { } }
        try {
            awaitCondition(description = "pet profile content") {
                val data = (profile.state.value as? UiState.Success<PetProfileData?>)?.data
                data?.pet?.id == petId && data.todayTasks.any { it.id == taskId }
            }
            var completed = false
            profile.toggleTask(
                environment.tasks.getTask(taskId)!!,
                completed = true,
                onDone = { completed = true }
            )
            awaitCondition(description = "pet profile completion") { completed }
            assertEquals(
                LocalDate.now().toEpochDay(),
                environment.tasks.getTask(taskId)!!.lastCompletedEpochDay
            )

            awaitCondition(description = "expense list content") {
                val data = (expenses.state.value as? UiState.Success<ExpensesData>)?.data
                data?.expenses?.any { it.id == expenseId } == true
            }
            val expenseData = (expenses.state.value as UiState.Success<ExpensesData>).data
            assertEquals(8.0, expenseData.total, 0.0)
            expenses.delete(environment.expenses.getExpense(expenseId)!!)
            awaitCondition(description = "expense deletion") {
                val data = (expenses.state.value as? UiState.Success<ExpensesData>)?.data
                data?.expenses?.none { it.id == expenseId } == true
            }
            assertNull(environment.expenses.getExpense(expenseId))

            profile.deletePet(environment.pets.getPet(petId)!!) { error ->
                throw AssertionError("Unexpected pet delete error: $error")
            }
            awaitCondition(description = "pet profile deleted") {
                profile.deleted.value
            }
            assertTrue(profile.deleted.value)
            assertNull(environment.pets.getPet(petId))
            assertNull(environment.tasks.getTask(taskId))
        } finally {
            profileCollector.cancelAndJoin()
            expenseCollector.cancelAndJoin()
            profile.viewModelScope.cancel()
            expenses.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `task detail exposes occurrence state and rejects a future completion`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Future Detail", "future-detail@example.com", "secret1")
            .getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Future Pet", species = Species.DOG.name)
        )
        val futureTask = CareTask(
            petId = petId,
            name = "Future one-off",
            category = TaskCategory.MEDICATION.name,
            frequency = Frequency.ONCE.name,
            startDateEpochDay = LocalDate.now().plusDays(1).toEpochDay(),
            timeMinutes = 8 * 60
        )
        val taskId = environment.tasks.save(futureTask)
        val viewModel = TaskDetailViewModel(
            environment.tasks,
            environment.pets,
            SavedStateHandle(mapOf(Routes.ARG_TASK_ID to taskId))
        )
        val collector: Job = launch { viewModel.state.collect { } }
        try {
            awaitCondition(description = "future task detail") {
                val data = (viewModel.state.value as? UiState.Success<TaskDetailData?>)?.data
                data?.task?.id == taskId
            }
            val detail = (viewModel.state.value as UiState.Success<TaskDetailData?>).data!!
            assertFalse(detail.occursToday)
            assertFalse(detail.completedToday)
            val initialLastCompleted = environment.tasks.getTask(taskId)!!.lastCompletedEpochDay
            assertNull(initialLastCompleted)

            var operationSettled = false
            viewModel.setCompleted(
                detail.task,
                completed = true,
                onError = { operationSettled = true },
                onDone = { operationSettled = true }
            )
            awaitCondition(description = "future completion operation") { operationSettled }
            assertEquals(
                initialLastCompleted,
                environment.tasks.getTask(taskId)!!.lastCompletedEpochDay
            )
        } finally {
            collector.cancelAndJoin()
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}