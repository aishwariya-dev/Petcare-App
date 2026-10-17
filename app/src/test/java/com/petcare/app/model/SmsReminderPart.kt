package com.petcare.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** First callback wins per SMS part, making duplicate broadcasts idempotent. */
@Entity(
    tableName = "sms_reminder_parts",
    indices = [Index(value = ["occurrenceId", "partIndex"], unique = true)]
)
data class SmsReminderPart(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val partIndex: Int,
    val status: String
)