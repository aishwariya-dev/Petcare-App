package com.petcare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.petcare.app.model.CareTask
import kotlinx.coroutines.flow.Flow

@Dao
interface CareTaskDao {

    @Insert
    suspend fun insert(task: CareTask): Long

    /**
     * Updates a task only through a pet owned by [ownerId]. Completion is
     * reconciled against the row's current schedule in the same SQL update:
     * unchanged frequency/start date keeps the current mark, while a schedule
     * change starts a fresh completion history.
     */
    @Query(
        """
        UPDATE care_tasks SET
            petId = :petId,
            name = :name,
            category = :category,
            frequency = :frequency,
            startDateEpochDay = :startDateEpochDay,
            timeMinutes = :timeMinutes,
            reminderEnabled = :reminderEnabled,
            smsReminderEnabled = :smsReminderEnabled,
            smsRecipient = :smsRecipient,
            supplies = :supplies,
            notes = :notes,
            imagePath = :imagePath,
            lastCompletedEpochDay = CASE
                WHEN frequency = :frequency
                 AND startDateEpochDay = :startDateEpochDay
                THEN lastCompletedEpochDay
                ELSE NULL
            END,
            createdAt = :createdAt
        WHERE id = :id
          AND petId IN (SELECT id FROM pets WHERE ownerId = :ownerId)
        """
    )
    suspend fun updateForOwner(
        id: Long,
        ownerId: Long,
        petId: Long,
        name: String,
        category: String,
        frequency: String,
        startDateEpochDay: Long,
        timeMinutes: Int,
        reminderEnabled: Boolean,
        smsReminderEnabled: Boolean,
        smsRecipient: String,
        supplies: String,
        notes: String,
        imagePath: String?,
        createdAt: Long
    ): Int

    /**
     * Restricts deletion through the owning pet rather than trusting a task
     * object supplied by the caller.
     */
    @Query(
        """
        DELETE FROM care_tasks
        WHERE id = :id
          AND petId IN (SELECT id FROM pets WHERE ownerId = :ownerId)
        """
    )
    suspend fun deleteByIdForOwner(id: Long, ownerId: Long): Int

    @Query(
        """
        SELECT care_tasks.* FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.id = :id AND pets.ownerId = :ownerId
        """
    )
    fun observeById(id: Long, ownerId: Long): Flow<CareTask?>

    @Query(
        """
        SELECT care_tasks.* FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.id = :id AND pets.ownerId = :ownerId
        """
    )
    suspend fun findById(id: Long, ownerId: Long): CareTask?

    @Query(
        """
        SELECT care_tasks.* FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.petId = :petId AND pets.ownerId = :ownerId
        ORDER BY care_tasks.timeMinutes, care_tasks.name
        """
    )
    fun observeForPet(petId: Long, ownerId: Long): Flow<List<CareTask>>

    @Query(
        """
        SELECT care_tasks.* FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.petId = :petId AND pets.ownerId = :ownerId
        """
    )
    suspend fun listForPet(petId: Long, ownerId: Long): List<CareTask>

    @Query("SELECT EXISTS(SELECT 1 FROM pets WHERE id = :petId AND ownerId = :ownerId)")
    suspend fun isPetOwnedBy(petId: Long, ownerId: Long): Boolean

    @Query(
        """
        SELECT care_tasks.* FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE pets.ownerId = :ownerId
        ORDER BY care_tasks.timeMinutes, care_tasks.name
        """
    )
    fun observeForOwner(ownerId: Long): Flow<List<CareTask>>

    @Query(
        """
        UPDATE care_tasks SET lastCompletedEpochDay = :epochDay
        WHERE id = :id
          AND petId IN (SELECT id FROM pets WHERE ownerId = :ownerId)
        """
    )
    suspend fun setLastCompleted(id: Long, ownerId: Long, epochDay: Long?): Int

    /** Un-completes every task that was ticked off on [epochDay] for this owner. */
    @Query(
        """
        UPDATE care_tasks SET lastCompletedEpochDay = NULL
        WHERE lastCompletedEpochDay = :epochDay
          AND petId IN (SELECT id FROM pets WHERE ownerId = :ownerId)
        """
    )
    suspend fun resetCompletedOn(ownerId: Long, epochDay: Long): Int

    /** Final direct ownership read used at the paid SMS boundary. */
    @Query(
        """SELECT care_tasks.* FROM care_tasks
           INNER JOIN pets ON pets.id = care_tasks.petId
           WHERE care_tasks.id = :id AND pets.ownerId = :ownerId"""
    )
    suspend fun findOwnedForSms(id: Long, ownerId: Long): CareTask?
}
