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
import com.example.chinna.ui.auth.AuthActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
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
                        navController.navigate(R.id.smartAdvisoryFragment, null, NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, false, true)
                            .setLaunchSingleTop(true)
                            .build())
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
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
        
        // Remove any pending session checks
        sessionHandler.removeCallbacksAndMessages(null)
    }
    
    override fun onResume() {
        super.onResume()
        // Update last activity time
        lastActivityTime = System.currentTimeMillis()
        
        // Save current time to preferences
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ACTIVITY, lastActivityTime).apply()
        
        // Schedule a session check after timeout
        sessionHandler.postDelayed({
            checkSessionTimeout()
        }, SESSION_WARNING - (System.currentTimeMillis() - lastActivityTime))
    }
    
    /**
     * Check if session has timed out and show warning or logout
     */
    private fun checkSessionTimeout() {
        try {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - lastActivityTime
            
            if (elapsedTime >= SESSION_TIMEOUT) {
                // Auto logout at timeout
                logout()
            } else if (elapsedTime >= SESSION_WARNING) {
                // Show warning dialog
                showSessionWarningDialog()
                
                // Check again after 1 minute
                sessionHandler.postDelayed({
                    checkSessionTimeout()
                }, 60000)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking session", e)
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
            .setPositiveButton("STAY LOGGED IN") { _, _ ->
                // Reset activity timer
                lastActivityTime = System.currentTimeMillis()
                val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_ACTIVITY, lastActivityTime).apply()
            }
            .setNegativeButton("LOGOUT NOW") { _, _ ->
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
        // Prevent multiple logout calls
        if (isLoggingOut) {
            Log.w("MainActivity", "Logout already in progress")
            return
        }
        
        isLoggingOut = true
        
        try {
            // Show a progress dialog to prevent user interaction during logout
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_Chinna_Dialog)
                .setTitle("Logging Out")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create()
            progressDialog.show()
            
            // IMPORTANT: The order matters because of Firebase Security Rules
            // 1. First disconnect any Firebase listeners/observers 
            //    to prevent security rule violations during logout
            disableAllFirebaseListeners()
            
            // 2. Clear shared preferences after disconnecting listeners
            getSharedPreferences("selected_crop", Context.MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            
            // 3. Clear local repository data BEFORE signing out
            //    (this ensures we're not making authenticated requests after token invalidation)
            lifecycleScope.launch {
                try {
                    // Clear local data first while still authenticated
                    userRepository.logout()
                    
                    // 4. NOW sign out of Firebase (after all data operations)
                    val auth = FirebaseAuth.getInstance()
                    auth.signOut()
                    
                    // 5. Force clear Firebase cache AFTER signout
                    try {
                        val firebaseContext = FirebaseAuth.getInstance().app.applicationContext
                        clearDirectory(File(firebaseContext.cacheDir, "firebase"))
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error clearing Firebase cache: ${e.message}", e)
                    }
                    
                    // Navigate to login with longer delay to ensure all cleanup is complete
                    Handler(Looper.getMainLooper()).postDelayed({
                        progressDialog.dismiss()
                        navigateToLogin()
                    }, 800) // Even longer delay for stability with security rules
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error during logout: ${e.message}", e)
                    progressDialog.dismiss()
                    isLoggingOut = false
                    showError("Logout failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to logout: ${e.message}", e)
            isLoggingOut = false
            showError("Logout failed: ${e.message}")
        }
    }
    
    // Disconnect any Firebase listeners to prevent security rule violations
    private fun disableAllFirebaseListeners() {
        try {
            // Cancel any pending Firestore listeners/operations
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // Force close any ongoing connections
            // This is a somewhat aggressive approach but helps prevent lingering connections
            firestore.terminate()
            
            // Give time for termination to complete
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Restart the connection for future use (will be dormant until next auth)
                    firestore.clearPersistence()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error clearing Firestore persistence: ${e.message}", e)
                }
            }, 300)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error disabling Firebase listeners: ${e.message}", e)
        }
    }
    
    private fun clearDirectory(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        clearDirectory(file)
                    }
                    file.delete()
                }
            }
        }
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    private fun navigateToLogin() {
        try {
            // Use different flags to prevent activity recreation loops
            val intent = Intent(this, AuthActivity::class.java)
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