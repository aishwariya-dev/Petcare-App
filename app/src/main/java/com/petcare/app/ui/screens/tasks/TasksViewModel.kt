package com.petcare.app.ui.screens.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.UpcomingItem
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.localDateChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TaskTab(val label: String) { TODAY("Today"), UPCOMING("Upcoming") }

data class PetTasksGroup(
    val pet: Pet,
    val tasks: List<CareTask>,
    val completedIds: Set<Long>,
    val overdueIds: Set<Long>
)

data class TasksData(
    val hasPets: Boolean,
    val today: LocalDate,
    val todayGroups: List<PetTasksGroup>,
    val upcoming: List<UpcomingItem>
)

/** Screen 11 plus the gesture behaviours from screens 15–19. */
class TasksViewModel(
    authRepository: AuthRepository,
    petRepository: PetRepository,
    private val taskRepository: TaskRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ownerId: StateFlow<Long?> = authRepository.currentUser
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tab = MutableStateFlow(
        if (savedStateHandle.get<String>(Routes.ARG_TAB) == "upcoming") TaskTab.UPCOMING else TaskTab.TODAY
    )
    val tab: StateFlow<TaskTab> = _tab.asStateFlow()

    fun selectTab(tab: TaskTab) {
        _tab.value = tab
        savedStateHandle[Routes.ARG_TAB] = if (tab == TaskTab.UPCOMING) "upcoming" else "today"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<TasksData>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(UiState.Loading)
            combine(
                localDateChanges(),
                petRepository.observePets(user.id),
                taskRepository.observeTasks(user.id)
            ) { today, pets, tasks -> UiState.Success(build(pets, tasks, today)) }
        }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private fun build(pets: List<Pet>, tasks: List<CareTask>, today: LocalDate): TasksData {
        val byPet = tasks.groupBy { it.petId }

        val groups = pets.mapNotNull { pet ->
            val todays = TaskSchedule.tasksOn(byPet[pet.id].orEmpty(), today)
            if (todays.isEmpty()) null
            else PetTasksGroup(
                pet = pet,
                tasks = todays,
                completedIds = todays.filter { TaskSchedule.isCompletedOn(it, today) }.map { it.id }.toSet(),
                overdueIds = todays.filter { TaskSchedule.isOverdue(it, today) }.map { it.id }.toSet()
            )
        }

        val petIndex = pets.associateBy { it.id }
        val upcoming = tasks.mapNotNull { task ->
            val pet = petIndex[task.petId] ?: return@mapNotNull null
            val next = TaskSchedule.nextOccurrenceAfter(task, today) ?: return@mapNotNull null
            UpcomingItem(task, pet, next)
        }.sortedWith(compareBy({ it.date }, { it.task.timeMinutes }))

        return TasksData(hasPets = pets.isNotEmpty(), today = today, todayGroups = groups, upcoming = upcoming)
    }

    fun setCompleted(
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

    private val mutationMutex = Mutex()
    private val pendingDeletes = mutableSetOf<Long>()

    fun delete(task: CareTask, onDeleted: () -> Unit, onError: (String) -> Unit) {
        if (!pendingDeletes.add(task.id)) return
        viewModelScope.launch {
            try {
                val deleted = mutationMutex.withLock { taskRepository.delete(task) }
                if (deleted) {
                    // Only now may the screen offer Undo.
                    onDeleted()
                } else {
                    onError("This task is no longer available.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.userMessage())
            } finally {
                pendingDeletes.remove(task.id)
            }
        }
    }

    fun restore(task: CareTask, onRestored: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val restored = mutationMutex.withLock { taskRepository.restore(task) }
                if (restored) onRestored()
                else onError("This task could not be restored.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.userMessage())
            }
        }
    }

    fun discardImage(task: CareTask, onError: (String) -> Unit) {
        mutate({ taskRepository.discardImage(task) }, {}, onError)
    }

    private fun mutate(action: suspend () -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                mutationMutex.withLock { action() }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.userMessage())
            }
        }
    }

    /** Shake gesture: clear today's completions after the user confirms. */
    fun resetToday(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val id = ownerId.value ?: run {
                onDone(0)
                return@launch
            }
            onDone(taskRepository.resetToday(id))
        }
    }
}
