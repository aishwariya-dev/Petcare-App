package com.petcare.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import org.json.JSONArray

@Entity(
    tableName = "pets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ownerId")]
)
data class Pet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: Long,
    val name: String,
    /** Stored as [Species.name]. */
    val species: String,
    val breed: String = "",
    val ageYears: Int? = null,
    val weightKg: Double? = null,
    val diet: String = "",
    val allergies: String = "",
    val vaccinations: String = "",
    val favouriteToys: String = "",
    val notes: String = "",
    /** Absolute path of a copied photo in app-private storage, or null. */
    val photoPath: String? = null,
    /**
     * JSON array containing the absolute paths of additional photos.
     *
     * The primary photo remains in [photoPath] so existing databases and all
     * existing avatar callers keep working.  This is deliberately a String
     * rather than a Room type converter: it keeps the migration additive and
     * gives old rows a valid value when the column is introduced.
     */
    @ColumnInfo(defaultValue = "'[]'")
    val galleryPhotoPaths: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

val Pet.speciesType: Species get() = Species.from(species)

/** "Golden Retriever" when a breed is set, otherwise the species label. */
val Pet.subtitle: String get() = breed.ifBlank { speciesType.label }

/**
 * Decodes the additional photo paths while tolerating rows written by older
 * builds or interrupted/manual migrations. Empty and duplicate paths are
 * discarded so callers can safely use the result as a gallery.
 */
val Pet.galleryPaths: List<String>
    get() = PetPhotoPaths.decode(galleryPhotoPaths)

/** Small JSON codec kept next to the persisted model to avoid a Room converter. */
object PetPhotoPaths {
    /** Primary image plus up to nine additional gallery images. */
    const val MAX_TOTAL_PHOTOS = 10

    fun decode(value: String?): List<String> = runCatching {
        val array = JSONArray(value?.ifBlank { "[]" } ?: "[]")
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotEmpty() }?.let { path ->
                    if (path !in this) add(path)
                }
            }
        }
    }.getOrDefault(emptyList())

    fun encode(paths: Iterable<String>): String {
        val array = JSONArray()
        paths.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach(array::put)
        return array.toString()
    }
}
