package com.petcare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.petcare.app.model.Expense
import com.petcare.app.util.ExpenseAmounts
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    /**
     * REPLACE is intentionally not used.  Replacing by primary key could
     * delete another account's row before the repository has checked its
     * owner.
     */
    @Insert
    suspend fun insert(expense: Expense): Long

    /**
     * Inserts only when the owner's finite positive amounts still have a
     * finite aggregate.  The check and write share one Room transaction so a
     * concurrent expense cannot make the checked total stale.
     */
    @Transaction
    suspend fun insertWithFiniteTotal(expense: Expense): Long {
        val total = listAmountsForOwner(expense.ownerId)
            .fold(expense.amount, Double::plus)
        require(total.isFinite()) { ExpenseAmounts.ERROR }
        return insert(expense)
    }

    /** Updates only the owner's row; replacing by id alone is not sufficient. */
    @Query(
        """
        UPDATE expenses SET
            ownerId = :ownerId,
            petId = :petId,
            amount = :amount,
            category = :category,
            description = :description,
            notes = :notes,
            dateEpochDay = :dateEpochDay,
            createdAt = :createdAt
        WHERE id = :id AND ownerId = :ownerId
        """
    )
    suspend fun updateForOwner(
        id: Long,
        ownerId: Long,
        petId: Long?,
        amount: Double,
        category: String,
        description: String,
        notes: String,
        dateEpochDay: Long,
        createdAt: Long
    ): Int

    /**
     * Replaces an owner's row only when the resulting aggregate remains
     * finite.  The existing row is excluded by id, and the guard is
     * transactional with the update so a rejected edit leaves it intact.
     */
    @Transaction
    suspend fun updateWithFiniteTotal(
        id: Long,
        ownerId: Long,
        petId: Long?,
        amount: Double,
        category: String,
        description: String,
        notes: String,
        dateEpochDay: Long,
        createdAt: Long
    ): Int {
        if (findById(id, ownerId) == null) return 0
        val total = listAmountsForOwnerExcluding(ownerId, id)
            .fold(amount, Double::plus)
        require(total.isFinite()) { ExpenseAmounts.ERROR }
        return updateForOwner(
            id = id,
            ownerId = ownerId,
            petId = petId,
            amount = amount,
            category = category,
            description = description,
            notes = notes,
            dateEpochDay = dateEpochDay,
            createdAt = createdAt
        )
    }

    @Query("DELETE FROM expenses WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteByIdForOwner(id: Long, ownerId: Long): Int

    /** All expenses for the owner, most recent first. */
    @Query("SELECT * FROM expenses WHERE ownerId = :ownerId ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeForOwner(ownerId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id AND ownerId = :ownerId")
    suspend fun findById(id: Long, ownerId: Long): Expense?

    @Query("SELECT EXISTS(SELECT 1 FROM pets WHERE id = :petId AND ownerId = :ownerId)")
    suspend fun isPetOwnedBy(petId: Long, ownerId: Long): Boolean

    @Query("SELECT amount FROM expenses WHERE ownerId = :ownerId")
    suspend fun listAmountsForOwner(ownerId: Long): List<Double>

    @Query(
        "SELECT amount FROM expenses " +
            "WHERE ownerId = :ownerId AND id != :excludedId"
    )
    suspend fun listAmountsForOwnerExcluding(ownerId: Long, excludedId: Long): List<Double>

    /** Running total for the owner. */
    @Query("SELECT SUM(amount) FROM expenses WHERE ownerId = :ownerId")
    fun observeTotal(ownerId: Long): Flow<Double?>
}
