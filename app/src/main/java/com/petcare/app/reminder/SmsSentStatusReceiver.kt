package com.petcare.app.reminder

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.petcare.app.PetCareApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sent acknowledgements are not delivery receipts; no delivery status is inferred.
 * The reminder notification is re-posted with the real outcome, because the
 * modem's answer arrives after the notification was first shown.
 */
class SmsSentStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT_STATUS) return
        val occurrenceId = intent.getLongExtra(EXTRA_SMS_OCCURRENCE_ID, -1L)
        val partIndex = intent.getIntExtra(EXTRA_SMS_PART_INDEX, -1)
        val partCount = intent.getIntExtra(EXTRA_SMS_PART_COUNT, 1)
        val app = context.applicationContext as? PetCareApp
        if (occurrenceId <= 0L || partIndex < 0 || app == null) return
        val callbackResult = resultCode
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = app.container.smsReminderRepository
                val sent = callbackResult == Activity.RESULT_OK
                if (sent) {
                    repository.recordPartCallback(
                        occurrenceId, partIndex, true,
                        "SMS part $partIndex was accepted by the carrier; delivery is not confirmed."
                    )
                } else {
                    repository.recordPartCallback(
                        occurrenceId, partIndex, false,
                        "The carrier did not accept SMS part $partIndex: ${sendFailureReason(callbackResult)} (code $callbackResult)."
                    )
                }
                // Only the final part's success, or any failure, changes what
                // the user should read; a failure line is sticky.
                if (!sent || partIndex == partCount - 1) {
                    val occurrence = repository.findOccurrence(occurrenceId)
                    val task = occurrence?.let { repository.findOwnedTask(it.taskId, it.ownerId) }
                    if (task != null && task.reminderEnabled) {
                        val line = if (sent) {
                            "SMS sent to ${task.smsRecipient}"
                        } else {
                            "SMS not sent — ${sendFailureReason(callbackResult)}"
                        }
                        ReminderNotification.postTaskNotification(app, task, line)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Plain wording for the SmsManager result codes a phone is likely to report. */
internal fun sendFailureReason(code: Int): String = when (code) {
    SmsManager.RESULT_ERROR_NO_SERVICE -> "no mobile service"
    SmsManager.RESULT_ERROR_RADIO_OFF -> "mobile network is off (airplane mode?)"
    SmsManager.RESULT_ERROR_NULL_PDU -> "the message could not be encoded"
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS sending limit reached"
    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "this number is not allowed"
    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "the SIM did not accept it; choose a default SMS SIM in phone settings"
    else -> "the phone reported an error"
}
