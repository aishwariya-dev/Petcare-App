package com.petcare.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Records a money expense (food, vet, grooming, etc.) for the household. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val ownerId: Long,
    /** Which pet the expense is for; null = general household. */
    val petId: Long? = null,
    val amount: Double,
    /** ExpenseCategory.name stored as a String for Room. */
    val category: String,
    val description: String = "",
    val notes: String = "",
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
    val categoryType: ExpenseCategory get() = ExpenseCategory.from(category)
}
