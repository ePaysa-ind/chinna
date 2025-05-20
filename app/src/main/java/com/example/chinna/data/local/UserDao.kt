package com.example.chinna.data.local

import androidx.room.*
import com.example.chinna.data.local.database.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Query("SELECT * FROM users WHERE mobile = :mobile")
    suspend fun getUserByMobile(mobile: String): UserEntity?
    
    @Query("SELECT * FROM users ORDER BY createdAt DESC LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>
    
    @Query("SELECT * FROM users ORDER BY createdAt DESC LIMIT 1")
    suspend fun getCurrentUserSync(): UserEntity?
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}