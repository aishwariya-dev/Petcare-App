package com.petcare.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.User
import com.petcare.app.model.UpcomingItem
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.localDateChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PetToday(
    val pet: Pet,
    val tasks: List<CareTask>,
    val completedIds: Set<Long>
)

data class DashboardData(
    val user: User,
    val today: LocalDate,
    /** All pets in the household — drives the My Pets row on Home. */
    val allPets: List<Pet>,
    val petsToday: List<PetToday>,
    val hasPets: Boolean,
    val totalToday: Int,
    val completedToday: Int,
    val upcoming: List<UpcomingItem>,
    val userPhotoPath: String? = null
)

class DashboardViewModel(
    authRepository: AuthRepository,
    private val petRepository: PetRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val authenticatedUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<DashboardData>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(UiState.Loading)
            combine(
                localDateChanges(),
                petRepository.observePets(user.id),
                taskRepository.observeTasks(user.id)
            ) { today, pets, tasks ->
                UiState.Success(build(user, pets, tasks, today))
            }
        }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private fun build(
        user: User,
        pets: List<Pet>,
        tasks: List<CareTask>,
        today: LocalDate
    ): DashboardData {
        val byPet = tasks.groupBy { it.petId }

        val petsToday = pets.mapNotNull { pet ->
            val todays = TaskSchedule.tasksOn(byPet[pet.id].orEmpty(), today)
            if (todays.isEmpty()) null
            else PetToday(
                pet = pet,
                tasks = todays,
                completedIds = todays.filter { TaskSchedule.isCompletedOn(it, today) }.map { it.id }.toSet()
            )
        }

        val petIndex = pets.associateBy { it.id }
        val upcoming = tasks.mapNotNull { task ->
            val pet = petIndex[task.petId] ?: return@mapNotNull null
            val next = TaskSchedule.nextOccurrenceAfter(task, today) ?: return@mapNotNull null
            if (next.isAfter(today.plusDays(14))) null else UpcomingItem(task, pet, next)
        }.sortedWith(compareBy({ it.date }, { it.task.timeMinutes })).take(4)

        return DashboardData(
            user = user,
            today = today,
            allPets = pets,
            petsToday = petsToday,
            hasPets = pets.isNotEmpty(),
            totalToday = petsToday.sumOf { it.tasks.size },
            completedToday = petsToday.sumOf { it.completedIds.size },
            upcoming = upcoming,
            userPhotoPath = user.photoPath
        )
    }

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
}
