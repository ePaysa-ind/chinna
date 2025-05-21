package com.example.chinna.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.chinna.data.local.UserDao

@Database(
    entities = [UserEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    
    companion object {
        // Add soilType column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE users ADD COLUMN soilType TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // If column already exists, ignore
                    e.printStackTrace()
                }
            }
        }
        
        // Village to PIN code migration
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Improved logging for debugging
                    android.util.Log.d("AppDatabase", "Starting village to PIN code migration")
                    
                    // First check if the users table exists at all
                    val tableCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")
                    if (tableCheck.count == 0) {
                        android.util.Log.w("AppDatabase", "Users table doesn't exist, creating fresh")
                        tableCheck.close()
                        
                        // Just create a new table with the right schema
                        db.execSQL("CREATE TABLE users (" +
                            "mobile TEXT NOT NULL PRIMARY KEY, " +
                            "name TEXT NOT NULL, " +
                            "pinCode TEXT NOT NULL, " +
                            "acreage REAL NOT NULL, " +
                            "crop TEXT NOT NULL, " +
                            "sowingDate INTEGER NOT NULL, " +
                            "soilType TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL)")
                        
                        return // Nothing more to do
                    }
                    tableCheck.close()
                    
                    // Now check for column existence
                    val cursor = db.query("PRAGMA table_info(users)")
                    var villageColumnExists = false
                    var pinCodeColumnExists = false
                    var pinCodeColumnIndex = -1
                    var villageColumnIndex = -1
                    
                    // List all columns for debugging
                    val columns = mutableListOf<String>()
                    
                    while (cursor.moveToNext()) {
                        val columnIndex = cursor.getInt(cursor.getColumnIndex("cid"))
                        val columnName = cursor.getString(cursor.getColumnIndex("name"))
                        columns.add(columnName)
                        
                        if (columnName == "village") {
                            villageColumnExists = true
                            villageColumnIndex = columnIndex
                        }
                        if (columnName == "pinCode") {
                            pinCodeColumnExists = true
                            pinCodeColumnIndex = columnIndex
                        }
                    }
                    cursor.close()
                    
                    android.util.Log.d("AppDatabase", "Columns in users table: $columns")
                    android.util.Log.d("AppDatabase", "Village column exists: $villageColumnExists (index $villageColumnIndex)")
                    android.util.Log.d("AppDatabase", "PinCode column exists: $pinCodeColumnExists (index $pinCodeColumnIndex)")
                    
                    // If neither column exists, add pinCode
                    if (!villageColumnExists && !pinCodeColumnExists) {
                        android.util.Log.d("AppDatabase", "Neither village nor pinCode exists, adding pinCode column")
                        db.execSQL("ALTER TABLE users ADD COLUMN pinCode TEXT NOT NULL DEFAULT '000000'")
                        return // No migration needed
                    }
                    
                    // If both columns exist, ensure they have the same data
                    if (villageColumnExists && pinCodeColumnExists) {
                        android.util.Log.d("AppDatabase", "Both columns exist, ensuring pinCode has priority")
                        // Make sure village data is copied to pinCode if pinCode is empty
                        db.execSQL("UPDATE users SET pinCode = village WHERE pinCode = '' OR pinCode IS NULL")
                        return // No need for table recreation
                    }
                    
                    // Create a backup with correct structure
                    android.util.Log.d("AppDatabase", "Creating backup table")
                    db.execSQL("CREATE TABLE users_backup (" +
                        "mobile TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "pinCode TEXT NOT NULL, " +
                        "acreage REAL NOT NULL, " +
                        "crop TEXT NOT NULL, " +
                        "sowingDate INTEGER NOT NULL, " +
                        "soilType TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)")
                    
                    // Handle the village to pinCode migration
                    if (villageColumnExists && !pinCodeColumnExists) {
                        android.util.Log.d("AppDatabase", "Copying data with village renamed to pinCode")
                        // Copy data with village column renamed to pinCode
                        db.execSQL("INSERT INTO users_backup " +
                            "SELECT mobile, name, village as pinCode, acreage, crop, sowingDate, soilType, createdAt FROM users")
                    } else if (!villageColumnExists && pinCodeColumnExists) {
                        android.util.Log.d("AppDatabase", "Only pinCode exists, simple data copy")
                        // pinCode already exists, just copy the data
                        db.execSQL("INSERT INTO users_backup " +
                            "SELECT mobile, name, pinCode, acreage, crop, sowingDate, soilType, createdAt FROM users")
                    }
                    
                    // Replace the old table with the new one
                    android.util.Log.d("AppDatabase", "Replacing old table with backup")
                    db.execSQL("DROP TABLE users")
                    db.execSQL("ALTER TABLE users_backup RENAME TO users")
                    
                    android.util.Log.d("AppDatabase", "Migration completed successfully")
                    
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration failed: ${e.message}", e)
                    e.printStackTrace()
                    
                    // Attempt recovery by creating a clean table
                    try {
                        android.util.Log.w("AppDatabase", "Attempting recovery with clean table")
                        
                        // Check if users table exists after the error
                        val tableCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")
                        val tableExists = tableCheck.count > 0
                        tableCheck.close()
                        
                        if (!tableExists) {
                            android.util.Log.w("AppDatabase", "Users table missing after error, creating new")
                            db.execSQL("CREATE TABLE users (" +
                                "mobile TEXT NOT NULL PRIMARY KEY, " +
                                "name TEXT NOT NULL, " +
                                "pinCode TEXT NOT NULL, " +
                                "acreage REAL NOT NULL, " +
                                "crop TEXT NOT NULL, " +
                                "sowingDate INTEGER NOT NULL, " +
                                "soilType TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL)")
                        }
                    } catch (recoveryEx: Exception) {
                        android.util.Log.e("AppDatabase", "Recovery failed: ${recoveryEx.message}", recoveryEx)
                    }
                }
            }
        }
    }
}