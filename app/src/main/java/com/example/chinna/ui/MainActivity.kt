package com.example.chinna.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.chinna.R
import com.example.chinna.databinding.ActivityMainBinding
import com.example.chinna.data.repository.UserRepository
import com.example.chinna.ui.auth.AuthActivityUpdated
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
// import java.io.File // Unused import
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var isNavigating = false
    private var lastActivityTime: Long = 0
    
    @Inject
    lateinit var userRepository: UserRepository
    
    companion object {
        private const val SESSION_TIMEOUT = 10 * 60 * 1000L // 10 minutes
        private const val SESSION_WARNING = 8 * 60 * 1000L  // 8 minutes (2 minutes before timeout)
        private const val PREF_NAME = "session_prefs"
        private const val KEY_LAST_ACTIVITY = "last_activity_time"
    }
    
    // Session management
    private val sessionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Check if user is logged in
            if (!userRepository.isLoggedIn()) {
                navigateToLogin()
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
            navigateToLogin()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Set theme for popup menu to ensure white text on dark background
        binding.toolbar.popupTheme = R.style.Theme_Chinna_PopupMenu
        
        // Enable the options menu
        invalidateOptionsMenu()
        
        // Check if session expired (10 minutes)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedTime = prefs.getLong(KEY_LAST_ACTIVITY, 0L)
        val currentTime = System.currentTimeMillis()
        
        val shouldReturnToHome = savedTime > 0 && (currentTime - savedTime) > SESSION_TIMEOUT
        
        // Handle edge-to-edge display for Android 15+
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, 0)
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Navigate to home if session expired
        if (shouldReturnToHome) {
            navController.navigate(R.id.homeFragment, null, NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build())
        }
        
        // Listen for navigation changes to update selected item
        navController.addOnDestinationChangedListener { _, destination, _ ->
            isNavigating = true
            when (destination.id) {
                R.id.homeFragment -> binding.bottomNavigation.selectedItemId = R.id.homeFragment
                R.id.cameraFragment, R.id.resultFragment -> binding.bottomNavigation.selectedItemId = R.id.cameraFragment
                R.id.practicesFragment, R.id.practicesSummaryFragment -> binding.bottomNavigation.selectedItemId = R.id.practicesFragment
                R.id.smartAdvisoryFragment -> binding.bottomNavigation.selectedItemId = R.id.smartAdvisoryFragment
            }
            isNavigating = false
        }
        
        // Handle bottom navigation item selection
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (isNavigating) return@setOnItemSelectedListener true
            
            when (item.itemId) {
                R.id.homeFragment -> {
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        navController.navigate(R.id.homeFragment, null, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, false, true)
                            .setLaunchSingleTop(true)
                            .build())
                    }
                    true
                }
                R.id.cameraFragment -> {
                    if (navController.currentDestination?.id != R.id.cameraFragment) {
                        navController.navigate(R.id.cameraFragment, null, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, false, true) 
                            .setLaunchSingleTop(true)
                            .build())
                    }
                    true
                }
                R.id.practicesFragment -> {
                    if (navController.currentDestination?.id != R.id.practicesFragment) {
                        navController.navigate(R.id.practicesFragment, null, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, false, true)
                            .setLaunchSingleTop(true)
                            .build())
                    }
                    true
                }
                R.id.smartAdvisoryFragment -> {
                    if (navController.currentDestination?.id != R.id.smartAdvisoryFragment) {
                        Log.d("MainActivity", "Navigating to smartAdvisoryFragment")
                        try {
                            navController.navigate(R.id.smartAdvisoryFragment, null, NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, false, true)
                                .setLaunchSingleTop(true)
                                .build())
                            Log.d("MainActivity", "Successfully navigated to smartAdvisoryFragment")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error navigating to smartAdvisoryFragment", e)
                        }
                    } else {
                        Log.d("MainActivity", "Already on smartAdvisoryFragment")
                    }
                    true
                }
                R.id.historyFragment -> {
                    // First navigate to home fragment if needed
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        navController.navigate(R.id.homeFragment, null, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, false, true)
                            .setLaunchSingleTop(true)
                            .build())
                        
                        // Short delay to ensure home fragment is ready
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Then show history dialog
                            val homeFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.childFragmentManager?.primaryNavigationFragment as? com.example.chinna.ui.home.HomeFragment
                            homeFragment?.showHistoryDialog()
                        }, 300)
                    } else {
                        // Already on home fragment, show history dialog directly
                        val homeFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.childFragmentManager?.primaryNavigationFragment as? com.example.chinna.ui.home.HomeFragment
                        homeFragment?.showHistoryDialog()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save current time when app goes to background
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_ACTIVITY, currentTime).apply()
        lastActivityTime = currentTime // Ensure lastActivityTime is also updated
        Log.d("MainActivity", "onPause - Saved lastActivityTime: $lastActivityTime")
        
        // Remove any pending session checks to prevent them from firing while paused
        sessionHandler.removeCallbacksAndMessages(null)
        Log.d("MainActivity", "onPause - Removed session handler callbacks.")
    }
    
    override fun onResume() {
        super.onResume()
        // Update last activity time upon resuming
        lastActivityTime = System.currentTimeMillis()
        
        // Save current time to preferences
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ACTIVITY, lastActivityTime).apply()
        Log.d("MainActivity", "onResume - Updated lastActivityTime: $lastActivityTime")
        
        // Schedule a session warning check.
        // If onResume is called multiple times, previous callbacks are removed in onPause.
        sessionHandler.postDelayed(this::checkSessionStatus, SESSION_WARNING)
        Log.d("MainActivity", "onResume - Scheduled session warning check for ${SESSION_WARNING / 60000} minutes.")
    }
    
    /**
     * Checks the user's session status.
     * If the session has timed out, triggers logout.
     * If the session is close to timeout (warning period), shows a warning dialog.
     * Otherwise, schedules the next check.
     */
    private fun checkSessionStatus() {
        try {
            val currentTime = System.currentTimeMillis()
            val elapsedTimeSinceLastActive = currentTime - lastActivityTime
            
            Log.d("MainActivity", "checkSessionStatus - Elapsed time: ${elapsedTimeSinceLastActive / 1000}s, Timeout: ${SESSION_TIMEOUT / 1000}s, Warning: ${SESSION_WARNING / 1000}s")

            if (elapsedTimeSinceLastActive >= SESSION_TIMEOUT) {
                Log.i("MainActivity", "Session timeout reached. Logging out.")
                logout()
            } else if (elapsedTimeSinceLastActive >= SESSION_WARNING) {
                Log.i("MainActivity", "Session warning period reached. Showing warning dialog.")
                showSessionWarningDialog()
                // Schedule next check to catch the actual timeout if user doesn't interact
                val timeRemainingToTimeout = SESSION_TIMEOUT - elapsedTimeSinceLastActive
                sessionHandler.postDelayed(this::checkSessionStatus, timeRemainingToTimeout.coerceAtLeast(1000L)) // Check at least after 1s
                Log.d("MainActivity", "Scheduled next session check in ${timeRemainingToTimeout / 1000}s")
            } else {
                // This case should ideally not be reached if called only from the warning threshold onwards.
                // However, as a safeguard, reschedule for the warning period from now.
                val nextCheckDelay = SESSION_WARNING - elapsedTimeSinceLastActive
                sessionHandler.postDelayed(this::checkSessionStatus, nextCheckDelay.coerceAtLeast(SESSION_WARNING)) // Ensure it's not too frequent
                Log.d("MainActivity", "Session still active. Rescheduling warning check in ${nextCheckDelay / 60000} minutes.")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking session status", e)
        }
    }
    
    /**
     * Shows a dialog warning the user about upcoming session timeout with improved readability
     */
    private fun showSessionWarningDialog() {
        // Use a custom dialog style with black text on white background for better readability
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_Chinna_SessionDialog)
            .setTitle("Session Expiring Soon")
            .setMessage("Your session will expire in 2 minutes due to inactivity. Would you like to stay logged in?")
            .setPositiveButton("STAY LOGGED IN") { dialog, _ ->
                // User chose to stay logged in, reset activity timer and reschedule check
                lastActivityTime = System.currentTimeMillis()
                val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_ACTIVITY, lastActivityTime).apply()
                Log.d("MainActivity", "User chose to stay logged in. Reset lastActivityTime to $lastActivityTime")

                // Clear previous callbacks and schedule a new warning check
                sessionHandler.removeCallbacksAndMessages(null)
                sessionHandler.postDelayed(this::checkSessionStatus, SESSION_WARNING)
                Log.d("MainActivity", "Rescheduled session warning check for ${SESSION_WARNING / 60000} minutes.")
                dialog.dismiss()
            }
            .setNegativeButton("LOGOUT NOW") { dialog, _ ->
                Log.d("MainActivity", "User chose to logout from session warning dialog.")
                dialog.dismiss()
                logout()
            }
            .create()
            
        // Show dialog with improved styling
        dialog.show()
        
        // Set larger text size for message
        val textView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        textView?.textSize = 16f
        
        // Ensure text is visible with proper contrast - BLACK text on white background
        val titleView = dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)
        titleView?.setTextColor(android.graphics.Color.BLACK)
        textView?.setTextColor(android.graphics.Color.BLACK)
        
        // Set dialog background to white
        dialog.window?.decorView?.setBackgroundColor(android.graphics.Color.WHITE)
        
        // Set distinct button colors for better visibility
        val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        positiveButton.setTextColor(getColor(R.color.dark_primary)) // Dark green for "STAY LOGGED IN"
        
        val negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE) 
        negativeButton.setTextColor(android.graphics.Color.RED) // Red for "LOGOUT NOW"
    }
    
    
    // Flag to prevent multiple logout attempts
    private var isLoggingOut = false
    
    private fun logout() {
        // Prevent multiple logout calls.
        if (isLoggingOut) {
            Log.w("MainActivity", "Logout already in progress. Ignoring additional call.")
            return
        }
        isLoggingOut = true
        Log.i("MainActivity", "Logout process started.")

        // Stop any pending session checks immediately.
        sessionHandler.removeCallbacksAndMessages(null)
        Log.d("MainActivity", "Cleared session handler callbacks during logout.")

        // Show a progress dialog.
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_Chinna_Dialog)
            .setTitle("Logging Out")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // Perform logout operations.
        lifecycleScope.launch {
            try {
                // 1. Disable Firestore network access if this is a desired general policy during logout.
                // This step is kept based on original logic but should be reviewed if other Firebase services need active network.
                disableFirestoreNetwork() // Renamed for clarity

                // 2. Clear session-related preferences.
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().remove(KEY_LAST_ACTIVITY).apply()
                Log.d("MainActivity", "Cleared session activity time from SharedPreferences.")

                // 3. Perform UserRepository logout actions (e.g., clear specific user prefs, not DB data).
                userRepository.logout() // This now clears PrefsManager.saveUserLoggedIn(false)
                Log.d("MainActivity", "UserRepository logout completed (cleared login status).")

                // 4. Sign out from Firebase Authentication.
                FirebaseAuth.getInstance().signOut()
                Log.i("MainActivity", "Firebase Auth signOut successful.")

                // User data in Room DB is intentionally preserved by UserRepository.logout().
                // The old comment about "Skipping Firebase cache clear" is less relevant now as data is primarily local.
                Log.d("MainActivity", "Local user data in Room is preserved as per design.")

                // Navigate to login. The delay is kept from original logic, consider reducing if possible.
                Handler(Looper.getMainLooper()).postDelayed({
                    progressDialog.dismiss()
                    navigateToLogin()
                }, 800) // Original delay for "stability"

            } catch (e: Exception) {
                Log.e("MainActivity", "Error during logout sequence: ${e.message}", e)
                progressDialog.dismiss()
                showError("Logout failed: ${e.message}")
                isLoggingOut = false // Reset flag on error to allow retry if appropriate
            }
        }
    }
    
    /**
     * Disables Firestore network access.
     * This is a general measure. If other Firebase services (not user-data related)
     * rely on Firestore network, this might be too broad.
     * Kept from original logic, assuming it's an intentional policy for logout.
     */
    private fun disableFirestoreNetwork() {
        try {
            FirebaseFirestore.getInstance().disableNetwork().addOnSuccessListener {
                Log.i("MainActivity", "Firestore network access disabled successfully.")
            }.addOnFailureListener { e ->
                Log.w("MainActivity", "Failed to disable Firestore network access: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception while trying to disable Firestore network: ${e.message}", e)
        }
    }
    
    // clearDirectory method appears to be unused. Removing it.
    // private fun clearDirectory(dir: File) {
    //     if (dir.exists() && dir.isDirectory) {
    //         val files = dir.listFiles()
    //         if (files != null) {
    //             for (file in files) {
    //                 if (file.isDirectory) {
    //                     clearDirectory(file)
    //                 }
    //                 file.delete()
    //             }
    //         }
    //     }
    // }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    private fun navigateToLogin() {
        try {
            // Use different flags to prevent activity recreation loops
            val intent = Intent(this, AuthActivityUpdated::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("CLEAN_LOGIN", true) // Signal this is a fresh login
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error navigating to login: ${e.message}", e)
            isLoggingOut = false
            showError("Navigation failed: ${e.message}")
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        try {
            menuInflater.inflate(R.menu.main_menu, menu)
            return true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error creating options menu", e)
            return false
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                // Create logout dialog using app's dark theme
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_Chinna_Dialog)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes") { _, _ -> logout() }
                    .setNegativeButton("No", null)
                    .create()
                    
                // Show dialog with proper styling
                dialog.show()
                
                // Ensure text is visible with proper contrast
                val titleView = dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)
                titleView?.setTextColor(android.graphics.Color.WHITE)
                
                val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
                messageView?.setTextColor(android.graphics.Color.WHITE)
                
                // Set distinct button colors
                val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                positiveButton.setTextColor(getColor(R.color.dark_accent))
                
                val negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE) 
                negativeButton.setTextColor(getColor(R.color.dark_accent))
                
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}