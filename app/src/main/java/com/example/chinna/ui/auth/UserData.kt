package com.example.chinna.ui.auth

data class UserData(
    val mobile: String,
    val name: String,
    val pinCode: String,
    val acreage: Double,
    val crop: String,
    val sowingDate: Long,
    val soilType: String
)