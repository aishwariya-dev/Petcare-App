package com.petcare.app.ui.screens.pets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.categoryType
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.localDateChanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PetProfileData(
    val pet: Pet,
    val today: LocalDate,
    val allTasks: List<CareTask>,
    val todayTasks: List<CareTask>,
    val completedTodayIds: Set<Long>,
    val medicalTasks: List<CareTask>
)

/** Screen 08 with the delete confirmation from screen 10. */
class PetProfileViewModel(
    private val petRepository: PetRepository,
    private val taskRepository: TaskRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val petId: Long = checkNotNull(savedStateHandle[Routes.ARG_PET_ID])

    val state: StateFlow<UiState<PetProfileData?>> = combine<LocalDate, Pet?, List<CareTask>, UiState<PetProfileData?>>(
        localDateChanges(),
        petRepository.observePet(petId),
        taskRepository.observeTasksForPet(petId)
    ) { today, pet, tasks ->
        if (pet == null) return@combine UiState.Success<PetProfileData?>(null)
        val todays = TaskSchedule.tasksOn(tasks, today)
        UiState.Success<PetProfileData?>(
            PetProfileData(
                pet = pet,
                today = today,
                allTasks = tasks,
                todayTasks = todays,
                completedTodayIds = todays.filter { TaskSchedule.isCompletedOn(it, today) }.map { it.id }.toSet(),
                medicalTasks = tasks.filter { it.categoryType.isMedical }
            )
        )
    }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun toggleTask(
        task: CareTask,
        completed: Boolean,
        onError: (String) -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                if (taskRepository.setCompleted(task, completed)) {
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

    fun deletePet(pet: Pet, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                petRepository.delete(pet)
                _deleted.value = true
            } catch (e: Exception) {
                onError(e.userMessage())
            }
        }
    }
}
