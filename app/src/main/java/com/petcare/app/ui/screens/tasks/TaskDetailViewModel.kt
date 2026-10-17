package com.petcare.app.ui.screens.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.data.repository.SmsReminderRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.localDateChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskDetailData(
    val task: CareTask,
    val pet: Pet,
    val today: LocalDate,
    val completedToday: Boolean,
    val occursToday: Boolean,
    val overdue: Boolean,
    val smsStatus: String? = null
)

/** Screens 13 (details), 15 (mark complete) and 16 (delete). */
class TaskDetailViewModel(
    private val taskRepository: TaskRepository,
    private val petRepository: PetRepository,
    savedStateHandle: SavedStateHandle,
    private val smsReminderRepository: SmsReminderRepository? = null
) : ViewModel() {

    private val taskId: Long = checkNotNull(savedStateHandle[Routes.ARG_TASK_ID])

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<TaskDetailData?>> = taskRepository.observeTask(taskId)
        .flatMapLatest { task ->
            if (task == null) return@flatMapLatest flowOf<UiState<TaskDetailData?>>(UiState.Success(null))
            combine(
                localDateChanges(),
                petRepository.observePet(task.petId),
                smsReminderRepository?.observeLatestStatus(task.id) ?: flowOf(null)
            ) { today, pet, smsStatus ->
                if (pet == null) UiState.Success<TaskDetailData?>(null)
                else {
                    UiState.Success<TaskDetailData?>(
                        TaskDetailData(
                            task = task,
                            pet = pet,
                            today = today,
                            completedToday = TaskSchedule.isCompletedOn(task, today),
                            occursToday = TaskSchedule.occursOn(task, today),
                            overdue = TaskSchedule.isOverdue(task, today),
                            smsStatus = smsStatus
                        )
                    )
                }
            }
        }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun setCompleted(
        task: CareTask,
        completed: Boolean,
        onError: (String) -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val detail = (state.value as? UiState.Success<TaskDetailData?>)?.data
                    ?.takeIf { it.task.id == task.id }
                val currentTask = detail?.task ?: task
                val today = detail?.today ?: LocalDate.now()
                if (completed && !TaskSchedule.isCompletedOn(currentTask, today) &&
                    !TaskSchedule.occursOn(currentTask, today)
                ) {
                    onError("This task is not scheduled today.")
                } else if (taskRepository.setCompleted(currentTask, completed, today)) {
                    onDone()
                } else {
                    onError("This task is no longer available.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.userMessage())
            }
        }
    }

    fun delete(task: CareTask, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (taskRepository.delete(task)) {
                    _deleted.value = true
                } else {
                    onError("This task is no longer available.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.userMessage())
            }
        }
    }
}
