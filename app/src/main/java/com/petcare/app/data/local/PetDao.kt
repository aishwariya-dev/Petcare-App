package com.petcare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.petcare.app.model.Pet
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {

    @Insert
    suspend fun insert(pet: Pet): Long

    /**
     * Updates every persisted pet field only while the row still belongs to
     * [ownerId].  This avoids an entity-only @Update becoming a cross-account
     * write if a stale screen submits a primary key from another account.
     */
    @Query(
        """
        UPDATE pets SET
            ownerId = :ownerId,
            name = :name,
            species = :species,
            breed = :breed,
            ageYears = :ageYears,
            weightKg = :weightKg,
            diet = :diet,
            allergies = :allergies,
            vaccinations = :vaccinations,
            favouriteToys = :favouriteToys,
            notes = :notes,
            photoPath = :photoPath,
            galleryPhotoPaths = :galleryPhotoPaths,
            createdAt = :createdAt
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun updateForOwner(
        id: Long,
        ownerId: Long,
        name: String,
        species: String,
        breed: String,
        ageYears: Int?,
        weightKg: Double?,
        diet: String,
        allergies: String,
        vaccinations: String,
        favouriteToys: String,
        notes: String,
        photoPath: String?,
        galleryPhotoPaths: String,
        createdAt: Long
    ): Int

    /**
     * Deletes by both primary key and owner.  Repository callers must never
     * use an entity-only @Delete here: an entity supplied by another local
     * account could otherwise delete a row with the same id.
     */
    @Query("DELETE FROM pets WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteByIdForOwner(id: Long, ownerId: Long): Int

    @Query("SELECT * FROM pets WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE")
    fun observeForOwner(ownerId: Long): Flow<List<Pet>>

    @Query("SELECT * FROM pets WHERE id = :id AND ownerId = :ownerId")
    fun observeById(id: Long, ownerId: Long): Flow<Pet?>

    @Query("SELECT * FROM pets WHERE id = :id AND ownerId = :ownerId")
    suspend fun findById(id: Long, ownerId: Long): Pet?
}
