package com.example.chinna.data.repository

import android.util.Log
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.local.database.UserEntity
import com.google.firebase.auth.FirebaseAuth
// FirebaseFirestore import removed as it's no longer used for user data
import kotlinx.coroutines.flow.Flow
// Unused coroutine imports removed:
// import kotlinx.coroutines.tasks.await
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.GlobalScope
// import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val prefsManager: PrefsManager
) {
    // Firestore instance and usersCollection removed - local Room DB is the sole source of truth for user data.
    private val auth = FirebaseAuth.getInstance()
    // private val firestore = FirebaseFirestore.getInstance() // Removed
    // private val usersCollection = firestore.collection("users") // Removed
    
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
            
            // val firebaseUid = currentUser.uid // firebaseUid no longer needed for Firestore
            // Log.d("UserRepository", "Firebase UID: $firebaseUid") // No longer needed
            
            // Step 2: Create user entity for local database
            val userEntity = UserEntity(
                mobile = mobile,
                name = name,
                pinCode = pinCode,
                acreage = acreage,
                crop = crop,
                sowingDate = sowingDate,
                soilType = soilType
                // createdAt is handled by UserEntity's default value
            )
            
            // Step 3: Firestore userData map removed - no longer saving to Firestore.
            
            // Step 4: Firestore saving step removed.
            // Log.d("UserRepository", "Saving user data to Firestore for UID: $firebaseUid") // Removed
            
            // Step 5: Save to local Room database (primary storage for user data)
            try {
                userDao.insertUser(userEntity)
                Log.d("UserRepository", "✅ User data saved successfully to local Room database")
            } catch (localDbError: Exception) {
                Log.e("UserRepository", "❌ Failed to save user data to local Room database: ${localDbError.message}", localDbError)
                // Saving to local DB is now critical, so re-throw the exception.
                throw Exception("Failed to save user data locally: ${localDbError.message}")
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
                is com.google.firebase.FirebaseNetworkException -> { // This might still be relevant if auth fails due to network
                    Log.e("UserRepository", "Network error: No internet connection during auth or other operations")
                    throw Exception("Network error: Please check your internet connection and try again")
                }
                // FirebaseFirestoreException case removed as Firestore is no longer used for user data persistence.
                // is com.google.firebase.firestore.FirebaseFirestoreException -> {
                //     Log.e("UserRepository", "Firestore error: ${e.message}")
                //     throw Exception("Cloud storage error: ${e.message}")
                // }
                else -> {
                    Log.e("UserRepository", "Unknown error during save: ${e.message}")
                    throw Exception("Failed to save user data: ${e.message}")
                }
            }
        }
    }
    
    // Removed saveUserToFirestore method - no longer syncing with Firestore (already commented out or removed prior to this change)
    
    // Removed Firestore synchronization methods (already commented out or removed prior to this change)
    
    /**
     * STUB METHOD: This method is kept for compatibility but no longer performs any Firestore operations.
     * User data is solely managed by the local Room database.
     * It's a stub that logs a message but doesn't perform any operations.
     */
    suspend fun syncUserFromFirestore() {
        Log.d("UserRepository", "STUB METHOD: Firestore sync (syncUserFromFirestore) is disabled - using local data only. This method does nothing.")
        // No longer syncing with Firestore - all data is kept locally only
    }
    
    /**
     * STUB METHOD: This method is kept for compatibility but no longer performs any Firestore operations.
     * User data is solely managed by the local Room database.
     * It's a stub that logs a message but doesn't perform any operations.
     */
    fun startFirestoreSync() {
        Log.d("UserRepository", "STUB METHOD: Firestore sync (startFirestoreSync) is disabled - using local data only. This method does nothing.")
        // No longer syncing with Firestore - all data is kept locally only
    }
    
    fun getCurrentUser(): Flow<UserEntity?> = userDao.getCurrentUser()
    
    /**
     * Get current user data directly from the local Room database.
     * Firestore is no longer used as a source for user data.
     */
    suspend fun getCurrentUserSync(): UserEntity? {
        Log.d("UserRepository", "Fetching current user data from local Room database...")
        
        // Step 1: Validate Firebase Auth state (still important to know who the user is)
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("UserRepository", "No authenticated Firebase user found. Cannot fetch user-specific data.")
            // Depending on app logic, you might want to return null or throw an error.
            // For now, returning null as no user is logged in.
            return null
        }
        // val firebaseUid = currentUser.uid // Not strictly needed if only fetching from local DB without UID key
        // Log.d("UserRepository", "Authenticated user: ${currentUser.email}, UID: $firebaseUid") // Optional logging

        // Step 2: Fetch directly from local Room database
        return try {
            // Assuming getCurrentUserSync from DAO fetches the user associated with the logged-in session
            // or a single user record if your app logic implies that.
            // If your DAO's getCurrentUserSync needs a UID, you would pass firebaseUid here.
            // For this example, assuming it fetches the relevant user without explicit UID.
            val localUser = userDao.getCurrentUserSync()
            if (localUser != null) {
                Log.d("UserRepository", "✅ User data found in local Room database.")
            } else {
                Log.w("UserRepository", "❌ No user data found in local Room database for the current user.")
            }
            localUser
        } catch (localDbError: Exception) {
            Log.e("UserRepository", "💥 Error fetching user data from local Room database: ${localDbError.message}", localDbError)
            // Depending on requirements, you might re-throw or handle (e.g., return null)
            // For now, returning null as data retrieval failed.
            null
        }
    }
    
    // tryLocalFallback method is no longer needed as getCurrentUserSync now directly fetches from local DB.
    // /**
    //  * Fallback method to get user data from local database when Firestore is unavailable
    //  */
    // private suspend fun tryLocalFallback(): UserEntity? {
    //     Log.d("UserRepository", "Attempting local database fallback...")
    //
    //     return try {
    //         val localUser = userDao.getCurrentUserSync()
    //         if (localUser != null) {
    //             Log.d("UserRepository", "✅ User data found in local database")
    //             localUser
    //         } else {
    //             Log.w("UserRepository", "❌ No user data found in local database either")
    //             null
    //         }
    //     } catch (localError: Exception) {
    //         Log.e("UserRepository", "💥 Local database fallback failed: ${localError.message}", localError)
    //         null
    //     }
    // }

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