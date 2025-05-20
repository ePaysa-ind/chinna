package com.example.chinna.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val mobile: String,
    val name: String,
    val pinCode: String,
    val acreage: Double,
    val crop: String,
    val sowingDate: Long,
    val soilType: String = "", // Add default value
    val createdAt: Long = System.currentTimeMillis()
)