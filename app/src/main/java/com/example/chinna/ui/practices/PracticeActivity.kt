package com.example.chinna.ui.practices

data class PracticeActivity(
    val weekPeriod: String,
    val activityType: String,
    val description: String,
    val inputsDosage: String,
    val timingMethod: String,
    val criticalNotes: String,
    val weekColor: Int
)