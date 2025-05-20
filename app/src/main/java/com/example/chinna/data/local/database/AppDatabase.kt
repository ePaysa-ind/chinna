package com.example.chinna.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.chinna.data.local.UserDao

@Database(
    entities = [UserEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN soilType TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // If column already exists, ignore
                    e.printStackTrace()
                }
            }
        }
    }
}