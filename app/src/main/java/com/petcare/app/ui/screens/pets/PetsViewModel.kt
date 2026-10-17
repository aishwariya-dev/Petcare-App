package com.petcare.app.ui.screens.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.Pet
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.localDateChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PetListItem(val pet: Pet, val tasksToday: Int, val completedToday: Int)

class PetsViewModel(
    authRepository: AuthRepository,
    petRepository: PetRepository,
    taskRepository: TaskRepository,
    dates: Flow<LocalDate> = localDateChanges()
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<List<PetListItem>>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(UiState.Loading)
            combine(
                petRepository.observePets(user.id),
                taskRepository.observeTasks(user.id),
                dates
            ) { pets, tasks, today ->
                val byPet = tasks.groupBy { it.petId }
                UiState.Success(
                    pets.map { pet ->
                        val todays = TaskSchedule.tasksOn(byPet[pet.id].orEmpty(), today)
                        PetListItem(
                            pet = pet,
                            tasksToday = todays.size,
                            completedToday = todays.count { TaskSchedule.isCompletedOn(it, today) }
                        )
                    }
                )
            }
        }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
