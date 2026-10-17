package com.petcare.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable paid-message boundary.  A unique occurrence is claimed before any
 * modem call; it is deliberately never retried after an ambiguous submission.
 */
@Entity(
    tableName = "sms_reminder_occurrences",
    indices = [Index(value = ["ownerId", "taskId", "dueEpochDay"], unique = true)]
)
data class SmsReminderOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: Long,
    val taskId: Long,
    val dueEpochDay: Long,
    val status: String = "CLAIMED",
    @ColumnInfo(defaultValue = "0") val expectedParts: Int = 0,
    val detail: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)