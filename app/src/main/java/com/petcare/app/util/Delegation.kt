package com.petcare.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.TaskCategory
import com.petcare.app.model.categoryType
import com.petcare.app.model.frequencyType
import com.petcare.app.model.subtitle
import java.time.LocalDate

/** What the owner chose to share on the Delegate screen. */
data class DelegationOptions(
    val includeChecklist: Boolean = true,
    val includeFeeding: Boolean = true,
    val includeMedication: Boolean = true,
    val includeNotes: Boolean = true
) {
    val hasSelection: Boolean
        get() = includeChecklist || includeFeeding || includeMedication || includeNotes

    /** Compact "1011"-style encoding used to pass options through navigation. */
    fun encode(): String = listOf(includeChecklist, includeFeeding, includeMedication, includeNotes)
        .joinToString("") { if (it) "1" else "0" }

    companion object {
        fun decode(value: String?): DelegationOptions {
            val v = value?.padEnd(4, '0') ?: "1111"
            return DelegationOptions(
                includeChecklist = v[0] == '1',
                includeFeeding = v[1] == '1',
                includeMedication = v[2] == '1',
                includeNotes = v[3] == '1'
            )
        }
    }
}

/** Builds the plain-text care plan that is handed to the messaging app. */
object DelegationMessageBuilder {

    fun build(
        pet: Pet,
        tasks: List<CareTask>,
        options: DelegationOptions,
        today: LocalDate = LocalDate.now()
    ): String {
        val lines = mutableListOf<String>()
        lines += "PetCare - ${pet.name}'s care plan (${DateTimeFormats.shortDate(today)})"
        lines += "${pet.name} - ${pet.subtitle}"

        if (options.includeChecklist) {
            val todays = TaskSchedule.tasksOn(tasks, today)
            lines += ""
            lines += "TODAY'S CHECKLIST"
            if (todays.isEmpty()) {
                lines += "Nothing scheduled today."
            } else {
                todays.forEach { task ->
                    val done = if (TaskSchedule.isCompletedOn(task, today)) " (done)" else ""
                    lines += "${DateTimeFormats.time(task.timeMinutes)} ${task.name}$done"
                }
            }
        }

        if (options.includeFeeding) {
            lines += ""
            lines += "FEEDING"
            val feeding = tasks.filter { it.categoryType == TaskCategory.FEEDING }
            if (feeding.isEmpty()) lines += "No feeding schedule set."
            feeding.forEach { lines += routineLine(it) }
            if (pet.diet.isNotBlank()) lines += "Diet: ${pet.diet}"
        }

        if (options.includeMedication) {
            lines += ""
            lines += "MEDICATION & HEALTH"
            val medical = tasks.filter { it.categoryType.isMedical }
            if (medical.isEmpty()) lines += "No medication scheduled."
            medical.forEach { lines += routineLine(it) }
        }

        if (options.includeNotes) {
            lines += ""
            lines += "NOTES"
            var any = false
            if (pet.allergies.isNotBlank()) { lines += "Allergies: ${pet.allergies}"; any = true }
            if (pet.favouriteToys.isNotBlank()) { lines += "Favourite toys: ${pet.favouriteToys}"; any = true }
            if (pet.notes.isNotBlank()) { lines += pet.notes.trim(); any = true }
            if (!any) lines += "No extra notes."
        }

        lines += ""
        lines += "Sent from PetCare"
        return lines.joinToString("\n")
    }

    private fun routineLine(task: CareTask): String {
        val schedule = when (task.frequencyType.name) {
            "ONCE" -> "on ${DateTimeFormats.shortDate(LocalDate.ofEpochDay(task.startDateEpochDay))}"
            "DAILY" -> "daily"
            "MONTHLY" -> "monthly"
            else -> "weekly"
        }
        val supplies = if (task.supplies.isNotBlank()) " - needs: ${task.supplies}" else ""
        val notes = if (task.notes.isNotBlank()) " (${task.notes.trim()})" else ""
        return "${task.name} - $schedule at ${DateTimeFormats.time(task.timeMinutes)}$supplies$notes"
    }
}

/**
 * Opens the device's SMS app with the recipient and body pre-filled.
 * PetCare never sends the message itself; the user reviews and sends it from Messages.
 */
object SmsLauncher {

    fun buildIntent(phoneNumber: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phoneNumber.trim()}")).apply {
            putExtra("sms_body", body)
        }

    /** Returns false when no messaging app could handle the intent. */
    fun open(context: Context, phoneNumber: String, body: String): Boolean {
        val intent = buildIntent(phoneNumber, body)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

/** Android share sheet for plain text; works without SMS permission or a SIM. */
object ShareLauncher {

    fun buildIntent(text: String, title: String = "Share"): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            title
        )

    /** Returns false when no app can receive the text. */
    fun shareText(context: Context, text: String, title: String = "Share"): Boolean =
        runCatching { context.startActivity(buildIntent(text, title)) }.isSuccess
}
