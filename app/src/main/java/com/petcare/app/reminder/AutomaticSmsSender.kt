package com.petcare.app.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import java.time.LocalDate

/** Device checks are intentionally narrow: no contacts, SMS history, role, or phone-state access. */
sealed interface SmsAvailability {
    data object Ready : SmsAvailability
    data object PermissionMissing : SmsAvailability
    data object TelephonyUnavailable : SmsAvailability
    data object DefaultSimUnavailable : SmsAvailability
}

interface AutomaticSmsSender {
    fun availability(): SmsAvailability

    /**
     * Hands the text to Android's SMS service. A normal return means submitted
     * to the service, never delivered. Per-part sent callbacks refine status.
     */
    suspend fun submit(
        task: CareTask,
        message: String,
        occurrenceId: Long,
        onPrepared: suspend (partCount: Int) -> Unit
    ): Result<Unit>
}

data class AutomaticSmsPayload(val recipient: String, val message: String)

/** Shared by the modem handoff, the task actions and the save confirmation preview. */
fun automaticSmsPayload(task: CareTask, pet: Pet, occurrence: LocalDate, today: LocalDate): AutomaticSmsPayload =
    AutomaticSmsPayload(
        recipient = task.smsRecipient,
        message = TaskSmsTemplate.build(task, pet, occurrence, today)
    )

class AndroidAutomaticSmsSender(private val context: Context) : AutomaticSmsSender {
    private val appContext = context.applicationContext

    override fun availability(): SmsAvailability {
        if (!hasSmsTelephonyFeature(appContext.packageManager, Build.VERSION.SDK_INT)) {
            return SmsAvailability.TelephonyUnavailable
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) return SmsAvailability.PermissionMissing
        // Dual-SIM phones set to "Ask every time" report no default SMS
        // subscription even though a SIM is ready. Android's default SmsManager
        // then asks the user to choose a SIM, so only a phone with no ready SIM
        // at all is reported as unavailable.
        if (SubscriptionManager.getDefaultSmsSubscriptionId() == SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
            !anySimReady()
        ) {
            return SmsAvailability.DefaultSimUnavailable
        }
        return SmsAvailability.Ready
    }

    /** SIM slot state needs no permission; `activeModemCount` is API 30+. */
    private fun anySimReady(): Boolean {
        val telephony = appContext.getSystemService(TelephonyManager::class.java) ?: return false
        val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            telephony.activeModemCount
        } else {
            @Suppress("DEPRECATION")
            telephony.phoneCount
        }
        if (slots <= 0) return telephony.simState == TelephonyManager.SIM_STATE_READY
        return (0 until slots).any { slot ->
            runCatching { telephony.getSimState(slot) }.getOrDefault(TelephonyManager.SIM_STATE_UNKNOWN) ==
                TelephonyManager.SIM_STATE_READY
        }
    }

    /**
     * The SIM used for the text. Android only shows its "choose a SIM" dialog
     * to a foreground app; a background alarm using the system default with no
     * default SMS SIM fails through the sent callback. So a concrete
     * subscription is always preferred: the default SMS SIM, else the phone's
     * general default SIM, else the data or calls SIM (all readable without
     * extra permissions). The system default is the last resort.
     */
    internal fun smsSubscriptionId(): Int =
        listOf(
            SubscriptionManager.getDefaultSmsSubscriptionId(),
            SubscriptionManager.getDefaultSubscriptionId(),
            SubscriptionManager.getDefaultDataSubscriptionId(),
            SubscriptionManager.getDefaultVoiceSubscriptionId()
        ).firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID

    private fun smsManager(): SmsManager {
        val subscriptionId = smsSubscriptionId()
        return if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    override suspend fun submit(
        task: CareTask,
        message: String,
        occurrenceId: Long,
        onPrepared: suspend (partCount: Int) -> Unit
    ): Result<Unit> = runCatching {
        check(availability() is SmsAvailability.Ready) { availabilityMessage(availability()) }
        val manager = smsManager()
        val body = message
        val parts = manager.divideMessage(body)
        // The template already keeps the text within the cap; this is the last
        // guard before anything is charged, in case the modem splits differently.
        check(parts.size <= TaskSmsTemplate.MAX_PARTS) {
            "The message would need ${parts.size} SMS parts; PetCare sends at most ${TaskSmsTemplate.MAX_PARTS}."
        }
        // Persist expected parts before handing any part to the modem. A sent
        // callback can therefore never be mistaken for whole-message success.
        onPrepared(parts.size)
        val sentIntents = ArrayList(parts.indices.map { part ->
            PendingIntent.getBroadcast(
                appContext,
                callbackRequestCode(occurrenceId, part),
                Intent(appContext, SmsSentStatusReceiver::class.java).apply {
                    action = ACTION_SMS_SENT_STATUS
                    data = Uri.parse("petcare://sms-sent/$occurrenceId/$part")
                    putExtra(EXTRA_SMS_OCCURRENCE_ID, occurrenceId)
                    putExtra(EXTRA_SMS_PART_INDEX, part)
                    putExtra(EXTRA_SMS_PART_COUNT, parts.size)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        })
        if (parts.size > 1) {
            manager.sendMultipartTextMessage(task.smsRecipient, null, parts, sentIntents, null)
        } else {
            manager.sendTextMessage(task.smsRecipient, null, body, sentIntents.single(), null)
        }
    }

    private fun callbackRequestCode(occurrenceId: Long, part: Int): Int =
        ((occurrenceId xor (occurrenceId ushr 32)) * 31 + part)
            .toInt().and(0x7fffffff).coerceAtLeast(1)
}

fun availabilityMessage(status: SmsAvailability): String = when (status) {
    SmsAvailability.Ready -> "SMS is ready."
    SmsAvailability.PermissionMissing ->
        "SMS permission is not allowed. Allow SMS for PetCare before enabling automatic SMS."
    SmsAvailability.TelephonyUnavailable ->
        "This device has no SMS service. Use a phone with SMS capability and a default SMS SIM."
    SmsAvailability.DefaultSimUnavailable ->
        "No SIM is ready for SMS. Insert a SIM card (and choose a default SMS SIM in system settings), then try again."
}

/** FEATURE_TELEPHONY_MESSAGING was added in API 33; legacy phones advertise FEATURE_TELEPHONY. */
internal fun hasSmsTelephonyFeature(packageManager: PackageManager, sdkInt: Int): Boolean =
    packageManager.hasSystemFeature(
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.FEATURE_TELEPHONY_MESSAGING
        } else {
            PackageManager.FEATURE_TELEPHONY
        }
    )

internal const val ACTION_SMS_SENT_STATUS = "com.petcare.app.reminder.SMS_SENT_STATUS"
internal const val EXTRA_SMS_OCCURRENCE_ID = "com.petcare.app.reminder.sms_occurrence_id"
internal const val EXTRA_SMS_PART_INDEX = "com.petcare.app.reminder.sms_part_index"
internal const val EXTRA_SMS_PART_COUNT = "com.petcare.app.reminder.sms_part_count"
