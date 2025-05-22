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
        Log.d("UserRepository", "Starting user save process - Mobile: $mobile, Name: $name")
        
        try {
            // Step 1: Validate Firebase Auth state
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("UserRepository", "Cannot save user data - Firebase user not authenticated")
                throw IllegalStateException("User must be authenticated before saving data")
            }
            
            val firebaseUid = currentUser.uid
            Log.d("UserRepository", "Firebase UID: $firebaseUid")
            
            // Step 2: Create user entity for local database backup
            val userEntity = UserEntity(
                mobile = mobile,
                name = name,
                pinCode = pinCode,
                acreage = acreage,
                crop = crop,
                sowingDate = sowingDate,
                soilType = soilType
            )
            
            // Step 3: Create user data map for Firestore
            val userData = hashMapOf(
                "mobile" to mobile,
                "name" to name,
                "pinCode" to pinCode,
                "acreage" to acreage,
                "crop" to crop,
                "sowingDate" to sowingDate,
                "soilType" to soilType,
                "createdAt" to System.currentTimeMillis(),
                "lastUpdated" to System.currentTimeMillis(),
                "firebaseUid" to firebaseUid
            )
            
            Log.d("UserRepository", "Saving user data to Firestore for UID: $firebaseUid")
            
            // Step 4: Save to Firestore first (primary storage)
            try {
                usersCollection.document(firebaseUid)
                    .set(userData)
                    .await()
                Log.d("UserRepository", "✅ User data saved successfully to Firestore")
            } catch (firestoreError: Exception) {
                Log.e("UserRepository", "❌ Failed to save to Firestore: ${firestoreError.message}", firestoreError)
                throw Exception("Failed to save user data to cloud storage: ${firestoreError.message}")
            }
            
            // Step 5: Save to local Room database as backup
            try {
                userDao.insertUser(userEntity)
                Log.d("UserRepository", "✅ User data saved successfully to local database")
            } catch (localDbError: Exception) {
                Log.w("UserRepository", "⚠️ Failed to save to local database (non-critical): ${localDbError.message}", localDbError)
                // Don't throw error for local database failure - Firestore is primary
            }
            
            // Step 6: Update user preferences
            try {
                prefsManager.saveUserLoggedIn(true)
                prefsManager.saveUserMobile(mobile)
                Log.d("UserRepository", "✅ User preferences updated successfully")
            } catch (prefsError: Exception) {
                Log.w("UserRepository", "⚠️ Failed to update preferences (non-critical): ${prefsError.message}", prefsError)
                // Don't throw error for preferences failure
            }
            
            Log.d("UserRepository", "🎉 User save process completed successfully")
            
        } catch (e: Exception) {
            Log.e("UserRepository", "💥 Critical error during user save process: ${e.message}", e)
            
            // Enhanced error reporting with specific error types
            when (e) {
                is IllegalStateException -> {
                    Log.e("UserRepository", "Authentication error: User not logged in properly")
                    throw Exception("Authentication error: Please log in again to continue")
                }
                is NumberFormatException -> {
                    Log.e("UserRepository", "Data validation error: Invalid number format")
                    throw Exception("Invalid data format: Please check your input values")
                }
                is com.google.firebase.FirebaseNetworkException -> {
                    Log.e("UserRepository", "Network error: No internet connection")
                    throw Exception("Network error: Please check your internet connection and try again")
                }
                is com.google.firebase.firestore.FirebaseFirestoreException -> {
                    Log.e("UserRepository", "Firestore error: ${e.message}")
                    throw Exception("Cloud storage error: ${e.message}")
                }
                else -> {
                    Log.e("UserRepository", "Unknown error during save: ${e.message}")
                    throw Exception("Failed to save user data: ${e.message}")
                }
            }
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
    
    /**
     * Get current user data with Firestore as primary source and local database as fallback
     * This ensures data is always available even with network issues
     */
    suspend fun getCurrentUserSync(): UserEntity? {
        Log.d("UserRepository", "Fetching current user data...")
        
        return try {
            // Step 1: Validate Firebase Auth state
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w("UserRepository", "No authenticated Firebase user found")
                return tryLocalFallback()
            }
            
            val firebaseUid = currentUser.uid
            Log.d("UserRepository", "Fetching user data for Firebase UID: $firebaseUid")
            
            // Step 2: Try to fetch from Firestore (primary source)
            try {
                val firestoreDoc = usersCollection.document(firebaseUid).get().await()
                
                if (firestoreDoc.exists()) {
                    Log.d("UserRepository", "✅ User data found in Firestore")
                    
                    // Convert Firestore document to UserEntity
                    val userData = firestoreDoc.data!!
                    val userEntity = UserEntity(
                        mobile = userData["mobile"] as? String ?: "",
                        name = userData["name"] as? String ?: "",
                        pinCode = userData["pinCode"] as? String ?: "",
                        acreage = (userData["acreage"] as? Number)?.toDouble() ?: 0.0,
                        crop = userData["crop"] as? String ?: "",
                        sowingDate = (userData["sowingDate"] as? Number)?.toLong() ?: 0L,
                        soilType = userData["soilType"] as? String ?: "",
                        createdAt = (userData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                    
                    // Step 3: Update local database with Firestore data for offline access
                    try {
                        userDao.insertUser(userEntity)
                        Log.d("UserRepository", "✅ Local database updated with Firestore data")
                    } catch (localError: Exception) {
                        Log.w("UserRepository", "⚠️ Failed to update local database: ${localError.message}")
                        // Continue anyway - we have the data from Firestore
                    }
                    
                    return userEntity
                    
                } else {
                    Log.w("UserRepository", "❌ No user document found in Firestore for UID: $firebaseUid")
                    return tryLocalFallback()
                }
                
            } catch (firestoreError: Exception) {
                Log.w("UserRepository", "⚠️ Firestore fetch failed: ${firestoreError.message}. Trying local fallback...")
                return tryLocalFallback()
            }
            
        } catch (e: Exception) {
            Log.e("UserRepository", "💥 Error in getCurrentUserSync: ${e.message}", e)
            return tryLocalFallback()
        }
    }
    
    /**
     * Fallback method to get user data from local database when Firestore is unavailable
     */
    private suspend fun tryLocalFallback(): UserEntity? {
        Log.d("UserRepository", "Attempting local database fallback...")
        
        return try {
            val localUser = userDao.getCurrentUserSync()
            if (localUser != null) {
                Log.d("UserRepository", "✅ User data found in local database")
                localUser
            } else {
                Log.w("UserRepository", "❌ No user data found in local database either")
                null
            }
        } catch (localError: Exception) {
            Log.e("UserRepository", "💥 Local database fallback failed: ${localError.message}", localError)
            null
        }
    }
    
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