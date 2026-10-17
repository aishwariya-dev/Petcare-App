package com.petcare.app.model

enum class Species(val label: String) {
    DOG("Dog"),
    CAT("Cat"),
    BIRD("Bird"),
    RABBIT("Rabbit"),
    FISH("Fish"),
    OTHER("Other");

    companion object {
        fun from(value: String?): Species = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class TaskCategory(val label: String, val isMedical: Boolean = false) {
    FEEDING("Feeding"),
    EXERCISE("Exercise"),
    MEDICATION("Medication", isMedical = true),
    GROOMING("Grooming"),
    CLEANING("Cleaning"),
    HEALTH_CHECK("Health check", isMedical = true),
    VACCINATION("Vaccination", isMedical = true),
    OTHER("Other");

    companion object {
        fun from(value: String?): TaskCategory = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class Frequency(val label: String, val description: String) {
    ONCE("Once", "One time"),
    DAILY("Daily", "Every day"),
    WEEKLY("Weekly", "Every week"),
    MONTHLY("Monthly", "Every month");

    companion object {
        fun from(value: String?): Frequency = entries.firstOrNull { it.name == value } ?: ONCE
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Category for a recorded pet-related expense. */
enum class ExpenseCategory(val label: String) {
    FOOD("Food & Treats"),
    GROOMING("Grooming"),
    VETERINARY("Veterinary"),
    MEDICATION("Medication"),
    TOYS("Toys & Accessories"),
    OTHER("Other");

    companion object {
        fun from(value: String?): ExpenseCategory = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/** Type of a geotagged pet-care location. */
enum class LocationType(val label: String, val emoji: String) {
    VETERINARY("Veterinary Clinic", "\uD83C\uDFE5"),
    GROOMING("Grooming Salon", "\u2702\uFE0F"),
    PARK("Dog Park", "\uD83C\uDF33"),
    PET_SHOP("Pet Shop", "\uD83D\uDED2"),
    OTHER("Other", "\uD83D\uDCCD");

    companion object {
        fun from(value: String?): LocationType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
