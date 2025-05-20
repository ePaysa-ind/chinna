package com.example.chinna.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.chinna.R
import com.example.chinna.data.local.database.UserEntity
import com.example.chinna.data.repository.UserRepository
import com.example.chinna.databinding.ActivityAuthUpdatedBinding
import com.example.chinna.ui.MainActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class AuthActivityUpdated : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthUpdatedBinding
    private val viewModel: AuthViewModel by viewModels()
    
    @Inject
    lateinit var auth: FirebaseAuth
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private var verificationId: String? = null
    private var selectedDate: Long? = null
    private var isExistingUser = false
    private var existingUserData: UserEntity? = null
    
    // Flag to prevent recreation loops
    companion object {
        private var IS_INITIALIZING = false
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent recreation loops
        if (IS_INITIALIZING) {
            Log.w("AuthActivity", "Already initializing, preventing recreation loop")
            setContentView(R.layout.activity_auth_updated) // Set simple content view
            Toast.makeText(this, "Loading authentication...", Toast.LENGTH_SHORT).show()
            return
        }
        
        IS_INITIALIZING = true
        
        try {
            // First inflate layout to avoid black screen on failure
            binding = ActivityAuthUpdatedBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Clear any existing Firebase connections
            clearFirebaseConnections()
            
            // Add a longer delay to ensure Firebase is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    initializeAuthentication()
                    IS_INITIALIZING = false // Reset flag once initialization is complete
                } catch (e: Exception) {
                    e.printStackTrace()
                    IS_INITIALIZING = false // Reset flag even on error
                    showError("Error initializing authentication: ${e.message}")
                }
            }, 500) // Longer delay for stability with security rules
        } catch (e: Exception) {
            // Critical error handling - try to recover by showing a simple layout
            e.printStackTrace()
            IS_INITIALIZING = false // Reset flag on error
            setContentView(R.layout.activity_auth_updated) // Fallback to XML inflation
            
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun clearFirebaseConnections() {
        try {
            // Force close any ongoing Firestore connections
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.terminate()
            
            // Check if we're transitioning from a recent logout
            val isAfterLogout = intent.getBooleanExtra("CLEAN_LOGIN", false)
            
            if (isAfterLogout) {
                Log.d("AuthActivity", "Clean login after logout detected")
                
                // Ensure all Firebase connections are closed and cleared
                FirebaseAuth.getInstance().signOut()
                
                // Clear any Firebase cache
                try {
                    val cacheDir = File(applicationContext.cacheDir, "firebase")
                    clearCache(cacheDir)
                } catch (e: Exception) {
                    Log.e("AuthActivity", "Cache clearing error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("AuthActivity", "Error clearing Firebase connections: ${e.message}")
        }
    }
    
    private fun clearCache(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        clearCache(file)
                    }
                    file.delete()
                }
            }
        }
    }
    
    private fun initializeAuthentication() {
        try {
            // Check if this is a clean login after logout
            val isCleanLogin = intent.getBooleanExtra("CLEAN_LOGIN", false)
            
            // Initialize Firebase Auth if needed
            if (!::auth.isInitialized) {
                auth = FirebaseAuth.getInstance()
            }
            
            // If it's a clean login, force sign out to ensure fresh state
            if (isCleanLogin) {
                try {
                    auth.signOut()
                    Log.d("AuthActivity", "Forced sign out for clean login")
                } catch (e: Exception) {
                    Log.e("AuthActivity", "Error during forced sign out: ${e.message}")
                }
            } else {
                // Only auto-login if not a forced clean login
                // Check if already logged in
                if (auth.currentUser != null) {
                    navigateToMain()
                    return
                }
            }
            
            setupInitialView()
            observeViewModel()
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Authentication error: ${e.message}")
        }
    }
    
    private fun setupInitialView() {
        // First, only show the mobile entry layout
        binding.mobileEntryLayout.root.visibility = View.VISIBLE
        binding.userDetailsLayout.root.visibility = View.GONE
        
        // Set up the continue button click listener
        binding.mobileEntryLayout.btnContinue.setOnClickListener {
            val mobileNumber = binding.mobileEntryLayout.etMobile.text.toString().trim()
            
            if (validateMobileNumber(mobileNumber)) {
                // Show loading indicator
                binding.mobileEntryLayout.progressBar.visibility = View.VISIBLE
                binding.mobileEntryLayout.btnContinue.isEnabled = false
                
                // Check if user exists
                checkIfUserExists(mobileNumber)
            }
        }
    }
    
    private fun validateMobileNumber(mobile: String): Boolean {
        if (mobile.length != 10) {
            showError("Please enter a valid 10-digit mobile number")
            return false
        }
        return true
    }
    
    private fun checkIfUserExists(mobile: String) {
        lifecycleScope.launch {
            try {
                val user = userRepository.getUserByMobile(mobile)
                
                if (user != null) {
                    // Existing user found
                    isExistingUser = true
                    existingUserData = user
                    setupUserDetailsWithExistingData(user)
                } else {
                    // New user
                    isExistingUser = false
                    existingUserData = null
                    setupUserDetailsForNewUser()
                }
                
                // Switch to user details view
                binding.mobileEntryLayout.root.visibility = View.GONE
                binding.userDetailsLayout.root.visibility = View.VISIBLE
                
            } catch (e: Exception) {
                Log.e("AuthActivity", "Error checking user: ${e.message}")
                showError("Error checking user data: ${e.message}")
                binding.mobileEntryLayout.progressBar.visibility = View.GONE
                binding.mobileEntryLayout.btnContinue.isEnabled = true
            }
        }
    }
    
    private fun setupUserDetailsWithExistingData(user: UserEntity) {
        // Show message that user exists
        binding.userDetailsLayout.tvUserStatus.visibility = View.VISIBLE
        binding.userDetailsLayout.tvUserStatus.text = "Welcome back! Please verify your information."
        
        // Prefill the form with existing data
        binding.userDetailsLayout.etName.setText(user.name)
        binding.userDetailsLayout.etPincode.setText(user.pinCode)
        binding.userDetailsLayout.etAcreage.setText(user.acreage.toString())
        binding.userDetailsLayout.etCrop.setText(user.crop)
        binding.userDetailsLayout.etSoilType.setText(user.soilType)
        
        // Set sowing date if available
        if (user.sowingDate > 0) {
            selectedDate = user.sowingDate
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.userDetailsLayout.etSowingDate.setText(formatter.format(Date(user.sowingDate)))
        }
        
        setupDetailViewComponents()
    }
    
    private fun setupUserDetailsForNewUser() {
        // Show message for new user
        binding.userDetailsLayout.tvUserStatus.visibility = View.VISIBLE
        binding.userDetailsLayout.tvUserStatus.text = "Welcome! Please complete your profile."
        
        // Clear all fields
        binding.userDetailsLayout.etName.setText("")
        binding.userDetailsLayout.etPincode.setText("")
        binding.userDetailsLayout.etAcreage.setText("")
        binding.userDetailsLayout.etCrop.setText("")
        binding.userDetailsLayout.etSoilType.setText("")
        binding.userDetailsLayout.etSowingDate.setText("")
        
        setupDetailViewComponents()
    }
    
    private fun setupDetailViewComponents() {
        // Setup crop autocomplete
        val crops = listOf("Okra", "Chillies", "Tomatoes", "Cotton", "Maize", "Soybean", "Rice", "Wheat", "Pulses")
        val cropAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, crops)
        (binding.userDetailsLayout.etCrop as? AutoCompleteTextView)?.setAdapter(cropAdapter)
        
        // Setup soil type autocomplete
        val soilTypes = listOf("Black", "Red", "Sandy loam")
        val soilAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, soilTypes)
        (binding.userDetailsLayout.etSoilType as? AutoCompleteTextView)?.setAdapter(soilAdapter)
        
        // Setup validation for name and PIN code
        setupInputValidation()
        
        // Setup date picker
        binding.userDetailsLayout.etSowingDate.setOnClickListener {
            showDatePicker()
        }
        
        // Setup send OTP button
        binding.userDetailsLayout.btnSendOtp.setOnClickListener {
            if (validateUserDetails()) {
                sendOtp()
            }
        }
    }
    
    private fun setupInputValidation() {
        // Name validation
        val namePattern = "^[a-zA-Z\\s]*$".toRegex()
        binding.userDetailsLayout.etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                if (!input.matches(namePattern) && input.isNotEmpty()) {
                    binding.userDetailsLayout.etName.setText(input.replace(Regex("[^a-zA-Z\\s]"), ""))
                    binding.userDetailsLayout.etName.setSelection(binding.userDetailsLayout.etName.text?.length ?: 0)
                    showError("Name cannot contain numbers or special characters")
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // PIN code validation
        val pinCodePattern = "^[1-9][0-9]{5}$".toRegex()
        binding.userDetailsLayout.etPincode.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                if (input.length == 6 && !input.matches(pinCodePattern)) {
                    binding.userDetailsLayout.etPincode.error = "Invalid PIN code format"
                    showError("PIN code must start with 1-9 followed by 5 digits")
                } else {
                    binding.userDetailsLayout.etPincode.error = null
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
    
    private fun showDatePicker() {
        // Set constraint to not allow future dates
        val constraintsBuilder = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .setValidator(DateValidatorPointBackward.now())
        
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Sowing Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(constraintsBuilder.build())
            .build()
            
        datePicker.addOnPositiveButtonClickListener { selection ->
            selectedDate = selection
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.userDetailsLayout.etSowingDate.setText(formatter.format(Date(selection)))
        }
        
        datePicker.show(supportFragmentManager, "date_picker")
    }
    
    private fun validateUserDetails(): Boolean {
        val name = binding.userDetailsLayout.etName.text.toString().trim()
        val pinCode = binding.userDetailsLayout.etPincode.text.toString().trim()
        val acreage = binding.userDetailsLayout.etAcreage.text.toString().trim()
        val crop = binding.userDetailsLayout.etCrop.text.toString().trim()
        val soilType = binding.userDetailsLayout.etSoilType.text.toString().trim()
        
        // Validation patterns
        val namePattern = "^[a-zA-Z\\s]+$".toRegex()
        val pinCodePattern = "^[1-9][0-9]{5}$".toRegex()
        
        return when {
            name.isBlank() -> {
                showError("Name is required")
                false
            }
            name.length < 3 -> {
                showError("Name must be at least 3 characters")
                false
            }
            !name.matches(namePattern) -> {
                showError("Name should not contain numbers")
                false
            }
            pinCode.isBlank() -> {
                showError("PIN code is required")
                false
            }
            pinCode.length != 6 -> {
                showError("PIN code must be 6 digits")
                false
            }
            !pinCode.matches(pinCodePattern) -> {
                showError("Invalid PIN code format")
                false
            }
            acreage.isBlank() || acreage.toDoubleOrNull() == null -> {
                showError("Enter valid acreage")
                false
            }
            acreage.toDouble() < 1 || acreage.toDouble() > 9 -> {
                showError("Acreage must be between 1 and 9")
                false
            }
            crop.isBlank() -> {
                showError("Select a crop")
                false
            }
            crop !in listOf("Okra", "Chillies", "Tomatoes", "Cotton", "Maize", "Soybean", "Rice", "Wheat", "Pulses") -> {
                showError("Select a valid crop from the list")
                false
            }
            soilType.isBlank() -> {
                showError("Select soil type")
                false
            }
            soilType !in listOf("Black", "Red", "Sandy loam") -> {
                showError("Select a valid soil type")
                false
            }
            // Sowing date is optional, but if provided, it cannot be in the future
            selectedDate != null && selectedDate!! > System.currentTimeMillis() -> {
                showError("Sowing date cannot be in the future")
                false
            }
            else -> true
        }
    }
    
    private fun sendOtp() {
        val phoneNumber = "+91${binding.mobileEntryLayout.etMobile.text}"
        
        // Configure phone auth with in-app reCAPTCHA
        auth.setLanguageCode("en")
        auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(false)
        
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }
                
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    showError("Verification failed: ${e.message}")
                }
                
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@AuthActivityUpdated.verificationId = verificationId
                    showOtpDialog()
                }
            })
            .build()
            
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
    
    private fun showOtpDialog() {
        val otpDialog = OtpDialogFragment.newInstance(
            phoneNumber = binding.mobileEntryLayout.etMobile.text.toString(),
            onOtpEntered = { otp ->
                verifyOtp(otp)
            }
        )
        otpDialog.show(supportFragmentManager, "otp_dialog")
    }
    
    private fun verifyOtp(otp: String) {
        verificationId?.let {
            val credential = PhoneAuthProvider.getCredential(it, otp)
            signInWithPhoneAuthCredential(credential)
        }
    }
    
    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveUserData()
                } else {
                    showError("Invalid OTP")
                }
            }
    }
    
    private fun saveUserData() {
        val userData = UserData(
            mobile = binding.mobileEntryLayout.etMobile.text.toString(),
            name = binding.userDetailsLayout.etName.text.toString(),
            pinCode = binding.userDetailsLayout.etPincode.text.toString(),
            acreage = binding.userDetailsLayout.etAcreage.text.toString().toDouble(),
            crop = binding.userDetailsLayout.etCrop.text.toString(),
            sowingDate = selectedDate ?: 0L,
            soilType = binding.userDetailsLayout.etSoilType.text.toString()
        )
        
        viewModel.saveUser(userData)
    }
    
    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> {
                    // Show loading
                    binding.userDetailsLayout.btnSendOtp.isEnabled = false
                }
                is AuthViewModel.AuthState.Success -> {
                    navigateToMain()
                }
                is AuthViewModel.AuthState.Error -> {
                    showError(state.message)
                    binding.userDetailsLayout.btnSendOtp.isEnabled = true
                }
            }
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    private fun showError(message: String) {
        android.util.Log.e("AuthActivity", message)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}