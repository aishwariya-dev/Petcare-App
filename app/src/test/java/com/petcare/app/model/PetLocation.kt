package com.petcare.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** A saved pet-related location (vet clinic, grooming salon, dog park, pet shop). */
@Entity(
    tableName = "pet_locations",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PetLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val ownerId: Long,
    val name: String,
    /** LocationType.name stored as String. */
    val type: String,
    val address: String = "",
    val notes: String = "",
    /** GPS latitude; null if not captured. */
    val latitude: Double? = null,
    /** GPS longitude; null if not captured. */
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val locationType: LocationType get() = LocationType.from(type)
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}
