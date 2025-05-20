package com.example.chinna.ui.practices

data class TaskItem(
    val title: String,
    val description: String,
    val date: String,
    val weekNumber: Int,
    val daysUntil: Int,
    val status: TaskStatus,
    val isCritical: Boolean = false
)

enum class TaskStatus {
    OVERDUE,
    TODAY,
    UPCOMING,
    FUTURE
}