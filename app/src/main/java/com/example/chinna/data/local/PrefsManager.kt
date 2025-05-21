package com.example.chinna.data.local

import android.content.Context
import com.example.chinna.model.PestResult
import com.example.chinna.model.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs = context.getSharedPreferences("chinna_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_USER = "user"
        private const val KEY_RESULTS = "results"
        private const val KEY_FREE_TRIALS = "free_trials"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_OFFLINE_QUEUE = "offline_queue"
        private const val KEY_USER_LOGGED_IN = "user_logged_in"
        private const val KEY_USER_MOBILE = "user_mobile"
        private const val KEY_SELECTED_CROP = "selected_crop"
        private const val KEY_SOWING_DATE = "sowing_date"
    }
    
    // User Management
    fun saveUser(user: User) {
        prefs.edit().putString(KEY_USER, gson.toJson(user)).apply()
    }
    
    fun getUser(): User? {
        val json = prefs.getString(KEY_USER, null)
        return json?.let { gson.fromJson(it, User::class.java) }
    }
    
    fun updateFreeTrials(count: Int) {
        prefs.edit().putInt(KEY_FREE_TRIALS, count).apply()
    }
    
    fun getFreeTrials(): Int {
        return prefs.getInt(KEY_FREE_TRIALS, 10)
    }
    
    // Results Cache
    fun saveResults(results: List<PestResult>) {
        prefs.edit().putString(KEY_RESULTS, gson.toJson(results)).apply()
    }
    
    fun getResults(): List<PestResult> {
        val json = prefs.getString(KEY_RESULTS, null)
        return if (json != null) {
            val type = object : TypeToken<List<PestResult>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
    
    fun addResult(result: PestResult) {
        val results = getResults().toMutableList()
        results.add(0, result) // Add to beginning
        if (results.size > 10) {
            results.removeLast() // Keep only last 10
        }
        saveResults(results)
    }
    
    // Offline Queue
    fun addToOfflineQueue(imagePath: String) {
        val queue = getOfflineQueue().toMutableList()
        queue.add(imagePath)
        prefs.edit().putString(KEY_OFFLINE_QUEUE, gson.toJson(queue)).apply()
    }
    
    fun getOfflineQueue(): List<String> {
        val json = prefs.getString(KEY_OFFLINE_QUEUE, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
    
    fun clearOfflineQueue() {
        prefs.edit().remove(KEY_OFFLINE_QUEUE).apply()
    }
    
    // Sync Management
    fun setLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }
    
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0)
    }
    
    // Clear all data
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    // Clear only login state but preserve other preferences
    fun clearLoginState() {
        prefs.edit()
            .remove(KEY_USER_LOGGED_IN)
            .apply()
    }
    
    // Login status management
    fun saveUserLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_USER_LOGGED_IN, isLoggedIn).apply()
    }
    
    fun isUserLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_USER_LOGGED_IN, false)
    }
    
    fun saveUserMobile(mobile: String) {
        prefs.edit().putString(KEY_USER_MOBILE, mobile).apply()
    }
    
    fun getUserMobile(): String? {
        return prefs.getString(KEY_USER_MOBILE, null)
    }
    
    // Crop and sowing date management
    fun saveSelectedCrop(crop: String) {
        prefs.edit().putString(KEY_SELECTED_CROP, crop).apply()
    }
    
    fun getSelectedCrop(): String? {
        return prefs.getString(KEY_SELECTED_CROP, null)
    }
    
    fun saveSowingDate(date: String) {
        prefs.edit().putString(KEY_SOWING_DATE, date).apply()
    }
    
    fun getSowingDate(): String? {
        return prefs.getString(KEY_SOWING_DATE, null)
    }
}