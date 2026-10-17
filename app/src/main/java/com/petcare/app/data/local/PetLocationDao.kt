package com.petcare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.petcare.app.model.PetLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface PetLocationDao {

    /**
     * REPLACE is intentionally not used.  A stale location object must never
     * delete and recreate a row belonging to another local account.
     */
    @Insert
    suspend fun insert(location: PetLocation): Long

    @Query(
        """
        UPDATE pet_locations SET
            ownerId = :ownerId,
            name = :name,
            type = :type,
            address = :address,
            notes = :notes,
            latitude = :latitude,
            longitude = :longitude,
            createdAt = :createdAt
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun updateForOwner(
        id: Long,
        ownerId: Long,
        name: String,
        type: String,
        address: String,
        notes: String,
        latitude: Double?,
        longitude: Double?,
        createdAt: Long
    ): Int

    @Query("DELETE FROM pet_locations WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteByIdForOwner(id: Long, ownerId: Long): Int

    /** All saved locations for the owner, alphabetical. */
    @Query("SELECT * FROM pet_locations WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE ASC")
    fun observeForOwner(ownerId: Long): Flow<List<PetLocation>>

    @Query("SELECT * FROM pet_locations WHERE id = :id AND ownerId = :ownerId")
    suspend fun findById(id: Long, ownerId: Long): PetLocation?
}
