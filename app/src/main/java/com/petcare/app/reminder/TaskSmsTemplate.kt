package com.petcare.app.reminder

import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.categoryType
import com.petcare.app.util.DateTimeFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one text used for automatic SMS, "Send SMS", "Share" and the save
 * preview, so what the owner reads on screen is exactly what the carer gets.
 *
 * The message never needs more than [MAX_PARTS] charged parts in either SMS
 * encoding. Optional content shrinks first (note, then supplies), then the
 * pet detail and footer go, and only then are the names themselves cut.
 */
object TaskSmsTemplate {
    /** Upper bound on charged SMS parts, whatever the alphabet. */
    const val MAX_PARTS = 3
    private const val ELLIPSIS = "..."

    fun build(
        task: CareTask,
        pet: Pet,
        occurrence: LocalDate,
        today: LocalDate,
        locale: Locale = Locale.getDefault()
    ): String {
        val note = task.notes.trim()
        val supplies = task.supplies.trim()
        val petName = pet.name.trim()
        val kind = pet.breed.trim().ifEmpty { pet.species.trim().lowercase().replaceFirstChar { it.titlecase() } }
        val age = pet.ageYears?.let { ", $it yrs" } ?: ""
        val whenLine = "${dayWord(occurrence, today, locale)} at ${DateTimeFormats.time(task.timeMinutes)}"

        fun render(noteChars: Int, suppliesChars: Int, compact: Boolean): String {
            val lines = listOfNotNull(
                "PetCare: " + if (compact) petName else "$petName ($kind$age)",
                "${task.categoryType.label} - ${task.name.trim()}",
                whenLine,
                clip(supplies, suppliesChars)?.let { "Needs: $it" },
                clip(note, noteChars)?.let { "Note: $it" },
                "Sent from PetCare".takeUnless { compact },
            )
            return SmsSegments.normalise(lines.joinToString("\n"))
        }

        val full = render(note.length, supplies.length, compact = false)
        if (fits(full)) return full
        longestFit(note.length) { render(it, supplies.length, compact = false) }?.let { return it }
        longestFit(supplies.length) { render(0, it, compact = false) }?.let { return it }
        var compact = render(0, 0, compact = true)
        // Last resort for extremely long names: cut from the end until it fits.
        while (!fits(compact) && compact.isNotEmpty()) compact = compact.dropLast(1)
        return compact
    }

    fun fits(text: String): Boolean = SmsSegments.parts(text) <= MAX_PARTS

    /** The text whole, clipped with an ellipsis, or null when it is dropped. */
    private fun clip(text: String, chars: Int): String? = when {
        text.isEmpty() || chars <= 0 -> null
        chars >= text.length -> text
        else -> text.take(chars).trimEnd() + ELLIPSIS
    }

    /**
     * Longest clip length in 0..[max] whose rendering fits, found by binary
     * search; null when the message does not fit even with the line dropped.
     */
    private inline fun longestFit(max: Int, render: (Int) -> String): String? {
        if (!fits(render(0))) return null
        var low = 0
        var high = max
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (fits(render(middle))) low = middle else high = middle - 1
        }
        return render(low)
    }

    private fun dayWord(occurrence: LocalDate, today: LocalDate, locale: Locale): String = when (occurrence) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> DateTimeFormatter.ofPattern("d MMM", locale).format(occurrence)
    }
}
