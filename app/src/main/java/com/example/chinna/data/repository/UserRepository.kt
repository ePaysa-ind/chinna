package com.example.chinna.data.repository

import android.util.Log
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.local.database.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val prefsManager: PrefsManager
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = firestore.collection("users")
    
    suspend fun saveUser(
        mobile: String,
        name: String,
        pinCode: String,
        acreage: Double,
        crop: String,
        sowingDate: Long,
        soilType: String
    ) {
        try {
            // Create user entity for local database
            val user = UserEntity(
                mobile = mobile,
                name = name,
                pinCode = pinCode,
                acreage = acreage,
                crop = crop,
                sowingDate = sowingDate,
                soilType = soilType
            )
            
            // Save to local Room database
            userDao.insertUser(user)
            
            // Save user preferences
            prefsManager.saveUserLoggedIn(true)
            prefsManager.saveUserMobile(mobile)
            
            // No longer saving to Firestore - keeping data only locally
            
            Log.d("UserRepository", "User data saved successfully to local database")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving user data: " + e.message, e)
            throw e
        }
    }
    
    // Removed saveUserToFirestore method - no longer syncing with Firestore
    
    // Removed Firestore synchronization methods
    
    /**
     * This method is kept for compatibility but no longer does anything with Firestore
     * It's a stub that logs a message but doesn't perform any operations
     */
    suspend fun syncUserFromFirestore() {
        Log.d("UserRepository", "Firestore sync disabled - using local data only")
        // No longer syncing with Firestore - all data is kept locally only
    }
    
    /**
     * This method is kept for compatibility but no longer does anything with Firestore
     * It's a stub that logs a message but doesn't perform any operations
     */
    fun startFirestoreSync() {
        Log.d("UserRepository", "Firestore sync disabled - using local data only")
        // No longer syncing with Firestore - all data is kept locally only
    }
    
    fun getCurrentUser(): Flow<UserEntity?> = userDao.getCurrentUser()
    
    suspend fun getCurrentUserSync(): UserEntity? = userDao.getCurrentUserSync()
    
    suspend fun getUserByMobile(mobile: String): UserEntity? = userDao.getUserByMobile(mobile)
    
    suspend fun logout() {
        // Don't delete user data from database anymore, just clear login state
        // userDao.deleteAllUsers() - Removed to preserve user data between sessions
        
        // Only clear login status in preferences, not all preferences
        prefsManager.saveUserLoggedIn(false)
        
        // Keep Firebase Auth logout handled in MainActivity
    }
    
    fun isLoggedIn(): Boolean = prefsManager.isUserLoggedIn()
}