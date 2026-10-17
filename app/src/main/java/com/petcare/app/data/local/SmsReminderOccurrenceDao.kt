package com.petcare.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.petcare.app.model.SmsReminderOccurrence
import com.petcare.app.model.SmsReminderPart
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsReminderOccurrenceDao {
    /** Returns -1 when this occurrence was already claimed by any callback. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(occurrence: SmsReminderOccurrence): Long

    @Query(
        """UPDATE sms_reminder_occurrences SET status = :status, detail = :detail, updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun updateStatus(id: Long, status: String, detail: String, updatedAt: Long): Int

    @Query(
        """UPDATE sms_reminder_occurrences SET status = 'SUBMITTED', expectedParts = :expectedParts,
           detail = :detail, updatedAt = :updatedAt WHERE id = :id AND status = 'CLAIMED'"""
    )
    suspend fun markSubmitted(id: Long, expectedParts: Int, detail: String, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPart(part: SmsReminderPart): Long

    @Query("SELECT * FROM sms_reminder_occurrences WHERE id = :id")
    suspend fun findOccurrence(id: Long): SmsReminderOccurrence?

    @Query("SELECT COUNT(*) FROM sms_reminder_parts WHERE occurrenceId = :occurrenceId AND status = 'SENT'")
    suspend fun sentPartCount(occurrenceId: Long): Int

    @Query("SELECT COUNT(*) FROM sms_reminder_parts WHERE occurrenceId = :occurrenceId AND status = 'FAILED'")
    suspend fun failedPartCount(occurrenceId: Long): Int

    @Transaction
    suspend fun recordPartCallback(id: Long, partIndex: Int, sent: Boolean, detail: String) {
        val before = findOccurrence(id) ?: return
        // Only PendingIntents created for this recorded submission are valid.
        // This also prevents a malformed/extra callback from downgrading an
        // already complete multipart result.
        if (partIndex !in 0 until before.expectedParts) return
        if (insertPart(SmsReminderPart(occurrenceId = id, partIndex = partIndex, status = if (sent) "SENT" else "FAILED")) <= 0) return
        val occurrence = findOccurrence(id) ?: return
        val now = System.currentTimeMillis()
        if (!sent) {
            // Failed is sticky: another part's later success must never make
            // a multipart message appear fully sent.
            updateStatus(id, "FAILED", detail, now)
        } else if (occurrence.status == "SUBMITTED" && occurrence.expectedParts > 0 &&
            failedPartCount(id) == 0 && sentPartCount(id) >= occurrence.expectedParts
        ) {
            updateStatus(id, "SENT", "All SMS parts were accepted by the carrier; delivery is not confirmed.", now)
        }
    }

    @Query(
        """SELECT status || CASE WHEN detail = '' THEN '' ELSE ': ' || detail END
           FROM sms_reminder_occurrences
           WHERE taskId = :taskId
           ORDER BY updatedAt DESC, id DESC LIMIT 1"""
    )
    fun observeLatestStatus(taskId: Long): Flow<String?>
}