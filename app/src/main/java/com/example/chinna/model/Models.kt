package com.example.chinna.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class Severity {
    HIGH,
    MEDIUM,
    LOW
}

data class User(
    val userId: String = "",
    val phoneNumber: String = "",
    val freeTrialsUsed: Int = 0,
    val isPremium: Boolean = false,
    val lastLoginDate: Long = 0,
    val registrationDate: Long = System.currentTimeMillis(),
    val soilType: String = "unknown"
)

data class PestResult(
    val id: String = "",
    val userId: String = "",
    val imagePath: String = "",
    val pestName: String = "",
    val severity: Severity = Severity.LOW,
    val summary: String = "",
    val treatment: String = "",
    val prevention: String = "",
    val confidence: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val plantName: String? = null
)

@Parcelize
data class Crop(
    val id: String,
    val name: String,
    val localName: String,
    val iconRes: Int,
    val specificIconRes: Int? = null,
    val description: String = ""
) : Parcelable

data class Practice(
    val weekNumber: Int,
    val title: String,
    val activities: List<String>,
    val criticalReminder: String? = null,
    val cropId: String
)