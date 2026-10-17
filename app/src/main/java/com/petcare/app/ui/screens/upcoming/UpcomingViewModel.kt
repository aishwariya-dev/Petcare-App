package com.petcare.app.ui.screens.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.UpcomingItem
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.localDateChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class UpcomingData(
    val today: LocalDate,
    val items: List<UpcomingItem>
)

class UpcomingViewModel(
    authRepository: AuthRepository,
    petRepository: PetRepository,
    taskRepository: TaskRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<UpcomingData>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(UiState.Loading)
            combine(
                localDateChanges(),
                petRepository.observePets(user.id),
                taskRepository.observeTasks(user.id)
            ) { today, pets, tasks ->
                val petIndex = pets.associateBy { it.id }
                val items = tasks.mapNotNull { task ->
                    val pet = petIndex[task.petId] ?: return@mapNotNull null
                    val next = TaskSchedule.nextOccurrenceAfter(task, today) ?: return@mapNotNull null
                    UpcomingItem(task, pet, next)
                }.sortedWith(compareBy({ it.date }, { it.task.timeMinutes }))
                UiState.Success(UpcomingData(today = today, items = items))
            }
        }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
