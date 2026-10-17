package com.petcare.app.ui.screens.dashboard

import com.petcare.app.model.Pet
import java.time.LocalTime

data class PetProgress(val pet: Pet, val done: Int, val total: Int)

/** What the Home ring and bars show; computed once per dashboard load. */
data class TodayProgress(val done: Int, val total: Int, val perPet: List<PetProgress>) {
    val remaining: Int get() = total - done
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val allDone: Boolean get() = total > 0 && done == total

    companion object {
        fun from(petsToday: List<PetToday>): TodayProgress {
            val perPet = petsToday
                .filter { it.tasks.isNotEmpty() }
                .map { PetProgress(it.pet, done = it.completedIds.size, total = it.tasks.size) }
            return TodayProgress(
                done = perPet.sumOf { it.done },
                total = perPet.sumOf { it.total },
                perPet = perPet
            )
        }
    }
}

/** "Good morning, Nitesh" — the first name only, by the phone's local time. */
fun greeting(fullName: String, now: LocalTime = LocalTime.now()): String {
    val firstName = fullName.trim().substringBefore(' ')
    val word = when {
        now.hour < 12 -> "Good morning"
        now.hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return "$word, $firstName"
}
