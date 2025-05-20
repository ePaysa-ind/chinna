package com.example.chinna

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.multidex.MultiDex
import com.example.chinna.data.remote.AuthService
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class ChinnaApplication : Application() {
    
    @Inject
    lateinit var authService: AuthService
    
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
        
        // Ensure app data directory access
        ensureAppDataAccess()
        
        // Initialize Firebase safely
        initializeFirebaseSafely()
        
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
        try {
            // Try multiple times with short delays to ensure initialization
            var attempts = 0
            var initialized = false
            
            while (attempts < 3 && !initialized) {
                try {
                    if (FirebaseApp.getApps(this).isEmpty()) {
                        FirebaseApp.initializeApp(this)
                        Log.d("ChinnaApp", "Firebase initialized successfully")
                        initialized = true
                    } else {
                        Log.d("ChinnaApp", "Firebase already initialized")
                        initialized = true
                    }
                } catch (e: Exception) {
                    attempts++
                    Log.e("ChinnaApp", "Firebase initialization attempt $attempts failed", e)
                    
                    if (attempts < 3) {
                        // Wait a bit before trying again
                        try {
                            Thread.sleep(200)
                        } catch (ie: InterruptedException) {
                            // ignore
                        }
                    }
                }
            }
            
            if (!initialized) {
                Log.e("ChinnaApp", "Firebase initialization ultimately failed after $attempts attempts")
            }
        } catch (e: Exception) {
            Log.e("ChinnaApp", "Firebase initialization failed", e)
        }
    }
}