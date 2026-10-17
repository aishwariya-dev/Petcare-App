package com.petcare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.petcare.app.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Long): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: Long): Flow<User?>

    /**
     * Updates the editable profile columns in one SQL statement.  Keeping
     * password and creation fields out of this update also prevents stale
     * profile screens from overwriting security data.
     */
    @Query(
        """
        UPDATE users
        SET name = :name, phoneNumber = :phoneNumber, photoPath = :photoPath
        WHERE id = :id
        """
    )
    suspend fun updateProfile(
        id: Long,
        name: String,
        phoneNumber: String,
        photoPath: String?
    ): Int

    /** Replaces the credential pair only; profile columns are left untouched. */
    @Query("UPDATE users SET passwordHash = :passwordHash, passwordSalt = :passwordSalt WHERE id = :id")
    suspend fun updatePassword(id: Long, passwordHash: String, passwordSalt: String): Int
}
