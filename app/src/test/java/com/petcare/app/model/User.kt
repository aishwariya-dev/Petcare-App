package com.petcare.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    /** Optional phone number; used to pre-fill SMS delegation. */
    val phoneNumber: String = "",
    /** Absolute path of a copied profile photo in app-private storage, or null. */
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
