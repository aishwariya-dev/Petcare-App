package com.petcare.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "care_tasks",
    foreignKeys = [
        ForeignKey(
            entity = Pet::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("petId")]
)
data class CareTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val name: String,
    /** Stored as [TaskCategory.name]. */
    val category: String,
    /** Stored as [Frequency.name]. */
    val frequency: String,
    /** First (or only) date the task occurs, as epoch day. */
    val startDateEpochDay: Long,
    /** Time of day in minutes since midnight. */
    val timeMinutes: Int,
    /** Whether a local notification should be scheduled for this task. */
    @ColumnInfo(defaultValue = "0")
    val reminderEnabled: Boolean = false,
    /** Explicit, per-occurrence authorization to use the phone's SMS SIM. */
    @ColumnInfo(defaultValue = "0")
    val smsReminderEnabled: Boolean = false,
    /** The recipient that consented to receive this task's automatic SMS. */
    @ColumnInfo(defaultValue = "''")
    val smsRecipient: String = "",
    val supplies: String = "",
    val notes: String = "",
    val imagePath: String? = null,
    /**
     * Epoch day on which the task was last marked complete, or null.
     * For recurring tasks "completed today" means this equals today's epoch day.
     */
    val lastCompletedEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

val CareTask.categoryType: TaskCategory get() = TaskCategory.from(category)
val CareTask.frequencyType: Frequency get() = Frequency.from(frequency)
val CareTask.startDate: LocalDate get() = LocalDate.ofEpochDay(startDateEpochDay)
val CareTask.time: LocalTime get() = LocalTime.ofSecondOfDay(timeMinutes * 60L)

/** Supplies are stored as a comma-separated string; expose them as a list. */
val CareTask.supplyList: List<String>
    get() = supplies.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** A task paired with the pet it belongs to, for lists that mix pets. */
data class TaskWithPet(val task: CareTask, val pet: Pet)

/** A future occurrence of a task, used by the Upcoming views. */
data class UpcomingItem(val task: CareTask, val pet: Pet, val date: LocalDate)
