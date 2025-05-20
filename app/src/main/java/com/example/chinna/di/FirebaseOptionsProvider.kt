package com.example.chinna.di

import android.content.Context
import android.util.Log
import com.example.chinna.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider class for Firebase initialization with secure API key storage
 */
@Singleton
class FirebaseOptionsProvider @Inject constructor() {

    companion object {
        private const val TAG = "FirebaseOptionsProvider"
    }

    /**
     * Initialize Firebase with options from BuildConfig instead of google-services.json
     */
    fun initializeFirebaseWithSecureKey(context: Context): Boolean {
        return try {
            // Only initialize if not already initialized
            if (FirebaseApp.getApps(context).isEmpty()) {
                val apiKey = BuildConfig.FIREBASE_API_KEY
                
                // Verify API key is available
                if (apiKey.isNullOrEmpty()) {
                    Log.e(TAG, "Firebase API key is empty. Please set FIREBASE_API_KEY in local.properties")
                    return false
                }
                
                // Get Firebase configuration from BuildConfig
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build()
                
                // Initialize with custom options
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase initialized successfully with secure API key")
                true
            } else {
                Log.d(TAG, "Firebase already initialized")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase with secure API key", e)
            false
        }
    }
}