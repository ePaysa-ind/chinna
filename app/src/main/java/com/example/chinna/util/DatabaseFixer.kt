package com.example.chinna.util

import android.content.Context
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to fix database issues that might arise from schema changes
 */
@Singleton
class DatabaseFixer @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "DatabaseFixer"
        private const val DB_NAME = "chinna_database"
    }
    
    /**
     * Deletes the Room database files completely to start fresh.
     * Should only be used in development or as a last resort when migration fails.
     */
    fun deleteRoomDatabase(): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val shm = File(dbFile.path + "-shm")
            val wal = File(dbFile.path + "-wal")
            val journal = File(dbFile.path + "-journal")
            
            var success = true
            
            if (dbFile.exists()) {
                success = success and dbFile.delete()
                Log.d(TAG, "Main database deleted: $success")
            }
            
            if (shm.exists()) {
                success = success and shm.delete()
                Log.d(TAG, "SHM file deleted: $success")
            }
            
            if (wal.exists()) {
                success = success and wal.delete()
                Log.d(TAG, "WAL file deleted: $success")
            }
            
            if (journal.exists()) {
                success = success and journal.delete()
                Log.d(TAG, "Journal file deleted: $success")
            }
            
            Log.d(TAG, "Database files deleted: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete database files", e)
            false
        }
    }
    
    /**
     * Check if the database exists and has issues by attempting to open it
     */
    fun hasDatabaseIntegrityIssues(): Boolean {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            return false // No database, no issues
        }
        
        try {
            // Try to open the database in read-only mode to check for integrity
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            
            // If we can open it without errors, check schema version
            val version = db.version
            Log.d(TAG, "Database version: $version")
            
            // Check if users table exists
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='users'", 
                null
            )
            val tableExists = cursor.count > 0
            cursor.close()
            
            // Close the database
            db.close()
            
            // Return false (no issues) if tables exist and version is correct
            return !tableExists || version < 3 // Return true only if users table missing or outdated schema
            
        } catch (e: Exception) {
            // If we get an exception, there are integrity issues
            Log.e(TAG, "Database integrity check failed", e)
            return true
        }
    }
}