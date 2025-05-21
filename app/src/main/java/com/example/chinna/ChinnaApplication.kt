package com.example.chinna

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.multidex.MultiDex
import com.example.chinna.data.remote.AuthService
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class ChinnaApplication : Application() {
    
    @Inject
    lateinit var authService: AuthService
    
    // Inject our database fixer
    @Inject
    lateinit var databaseFixer: com.example.chinna.util.DatabaseFixer
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Enable multi-dex to handle larger code
        MultiDex.install(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Prevent disk I/O on main thread issues 
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        
        // Sleep a bit to ensure systems are ready
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            // ignore
        }
        
        // Check and fix database issues before any database access
        fixDatabaseIssues()
        
        // Ensure app data directory access
        ensureAppDataAccess()
        
        // Initialize Firebase safely - this is handled by Hilt
        initializeFirebaseSafely()
        
        // Initialize Firebase App Check AFTER Firebase initialization
        try {
            // Make sure Firebase is initialized before setting up App Check
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                val firebaseAppCheck = FirebaseAppCheck.getInstance()
                
                // For production, use Play Integrity
                Log.d("ChinnaApp", "Using Play Integrity for Firebase App Check")
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                
                Log.d("ChinnaApp", "Firebase App Check initialized successfully")
            } else {
                Log.e("ChinnaApp", "Firebase not initialized, cannot set up App Check")
            }
        } catch (e: Exception) {
            Log.e("ChinnaApp", "Failed to initialize Firebase App Check", e)
        }
        
        // Configure Firebase Auth - this is critical for consistent OTP verification
        // Detection if the user is an admin will be handled inside the method
        try {
            val sharedPrefs = getSharedPreferences("admin_settings", Context.MODE_PRIVATE)
            val isAdmin = sharedPrefs.getBoolean("is_admin", false)
            authService.configureFirebaseAuth(this, isAdmin)
            Log.d("ChinnaApp", "Firebase Auth configured successfully. Admin mode: $isAdmin")
        } catch (e: Exception) {
            Log.e("ChinnaApp", "Failed to configure Firebase Auth", e)
        }
    }
    
    /**
     * Check and fix any database integrity issues
     */
    private fun fixDatabaseIssues() {
        try {
            // Only delete database if there are actual integrity issues
            if (databaseFixer.hasDatabaseIntegrityIssues()) {
                Log.w("ChinnaApp", "Database integrity issues detected, attempting to fix")
                val deleted = databaseFixer.deleteRoomDatabase()
                Log.d("ChinnaApp", "Database deleted: $deleted")
            } else {
                Log.d("ChinnaApp", "Database integrity check passed, no action needed")
            }
        } catch (e: Exception) {
            Log.e("ChinnaApp", "Error fixing database", e)
        }
    }
    
    private fun ensureAppDataAccess() {
        try {
            val dataDir = applicationInfo.dataDir
            val dir = File(dataDir)
            if (!dir.exists()) {
                dir.mkdirs()
                Log.d("ChinnaApp", "Created app data directory")
            }
        } catch (e: Exception) {
            Log.e("ChinnaApp", "Error ensuring app data access", e)
        }
    }
    
    private fun initializeFirebaseSafely() {
        // We no longer need this method as Firebase initialization is done in the AppModule
        // using FirebaseOptionsProvider which uses the secure API key from local.properties
        // This is left as a stub for backward compatibility
        Log.d("ChinnaApp", "Firebase initialization delegated to FirebaseOptionsProvider")
    }
}