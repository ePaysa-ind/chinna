package com.example.chinna.ui.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.core.content.ContextCompat
import com.example.chinna.R
import com.google.firebase.auth.FirebaseAuth

class RecaptchaActivity : Activity() {
    
    companion object {
        const val EXTRA_URL = "extra_url"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set a custom title and theme for the activity
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE)
        setContentView(R.layout.activity_recaptcha)
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.recaptcha_title)
        
        // Find the title text view and set it to "Security Verification"
        val titleTextView = findViewById<TextView>(R.id.recaptcha_title_text)
        titleTextView?.text = "Security Verification"
        
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        
        // Configure Chrome Custom Tab with improved styling
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(this, R.color.dark_primary))
            .setNavigationBarColor(ContextCompat.getColor(this, R.color.dark_background))
            .setSecondaryToolbarColor(ContextCompat.getColor(this, R.color.dark_primary))
            .build()
            
        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorSchemeParams)
            .setShowTitle(false)  // Hide the URL/title completely
            .setUrlBarHidingEnabled(true)  // Hide URL bar on scroll
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)  // Disable share button
            .setInstantAppsEnabled(false)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .build()
            
        // Hide URL by setting a custom title
        customTabsIntent.intent.putExtra("android.intent.extra.TITLE", "Security Check")
        
        // Launch the custom tab
        try {
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            e.printStackTrace()
            // If launching fails, finish the activity to avoid a hanging screen
            finish()
        }
    }
}