package com.example.chinna.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
// import android.os.Handler // Unused
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
// import android.widget.Toast // Unused, using Snackbar
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
// import com.google.firebase.firestore.FirebaseFirestore // Unused
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
// import kotlinx.coroutines.delay // No longer using manual delay in onCreate
import java.text.SimpleDateFormat
import java.util.*
// import java.io.File // clearCache method to be removed
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
    
    // Flag IS_INITIALIZING removed, relying on standard lifecycle and Hilt injection.
    // companion object {
    //     private var IS_INITIALIZING = false
    // }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthUpdatedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d("AuthActivity", "onCreate called.")
        
        // Handle potential logout scenario first.
        handleCleanLogin()
        
        // Initialize authentication logic.
        initializeAuthentication()
    }
    
    private fun handleCleanLogin() {
        val isCleanLogin = intent.getBooleanExtra("CLEAN_LOGIN", false)
        if (isCleanLogin) {
            Log.d("AuthActivity", "CLEAN_LOGIN intent extra is true. Signing out Firebase user.")
            auth.signOut() // Sign out the user to ensure a fresh login state.
            // Optionally, clear any other session-specific data here if needed.
        } else {
            Log.d("AuthActivity", "Normal app start or return to AuthActivity. Firebase session may persist if user was already logged in.")
        }
    }
    
    // clearCache method removed as it was a stub and not used.
    
    private fun initializeAuthentication() {
        // Hilt injects 'auth' and 'userRepository'.
        // Check current Firebase authentication state.
        val currentFirebaseUser = auth.currentUser

        if (currentFirebaseUser != null) {
            Log.d("AuthActivity", "User is already authenticated with Firebase: ${currentFirebaseUser.phoneNumber}")
            val mobileNumber = currentFirebaseUser.phoneNumber?.replace("+91", "")
            if (!mobileNumber.isNullOrEmpty()) {
                lifecycleScope.launch {
                    try {
                        val user = userRepository.getUserByMobile(mobileNumber)
                        if (user != null) {
                            Log.d("AuthActivity", "User also found in local Room database. Navigating to MainActivity.")
                            navigateToMain()
                        } else {
                            Log.w("AuthActivity", "User authenticated with Firebase but no profile in local Room DB. Proceeding to profile creation.")
                            // User is authenticated but needs to create a local profile.
                            // Pre-fill mobile, other fields will be empty.
                            binding.mobileEntryLayout.etMobile.setText(mobileNumber)
                            isExistingUser = false // Treat as new for profile form purposes
                            existingUserData = null
                            setupUserDetailsForNewUser() // Show details form
                            binding.mobileEntryLayout.root.visibility = View.GONE
                            binding.userDetailsLayout.root.visibility = View.VISIBLE
                            observeViewModel() // Start observing for save operation
                        }
                    } catch (e: Exception) {
                        Log.e("AuthActivity", "Error checking local database for authenticated user: ${e.message}", e)
                        showError("Error accessing local user data: ${e.message}")
                        // Fallback to showing initial view if DB check fails
                        setupInitialUiVisible()
                        observeViewModel()
                    }
                }
            } else {
                // Firebase user exists but phone number is null/empty (should not happen with phone auth).
                Log.e("AuthActivity", "Firebase user exists but phone number is missing. Forcing new login.")
                setupInitialUiVisible()
                observeViewModel()
            }
        } else {
            // No Firebase user authenticated, proceed with normal login/registration flow.
            Log.d("AuthActivity", "No Firebase user authenticated. Showing initial login/registration view.")
            setupInitialUiVisible()
            observeViewModel()
        }
    }
    
    private fun setupInitialUiVisible() {
        // Renamed from setupInitialView to avoid confusion with view model setup
        // First, only show the mobile entry layout
        binding.mobileEntryLayout.root.visibility = View.VISIBLE
        binding.userDetailsLayout.root.visibility = View.GONE
        
        // Show privacy notice on first launch
        showPrivacyNotice()
        
        // Set up the continue button click listener
        binding.mobileEntryLayout.btnContinue.setOnClickListener {
            val mobileNumber = binding.mobileEntryLayout.etMobile.text.toString().trim()
            
            if (validateMobileNumber(mobileNumber)) {
                binding.mobileEntryLayout.progressBar.visibility = View.VISIBLE
                binding.mobileEntryLayout.btnContinue.isEnabled = false
                checkIfUserExists(mobileNumber) // This will handle UI transitions
            }
        }
    }
    
    /**
     * Show privacy notice to inform users about data storage
     */
    private fun showPrivacyNotice() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasShownPrivacyNotice = sharedPrefs.getBoolean("privacy_notice_shown", false)
        
        if (!hasShownPrivacyNotice) {
            MaterialAlertDialogBuilder(this)
                .setTitle("📋 Data Storage Notice")
                .setMessage("""
                    Your farming data (name, crop details, acreage, etc.) is securely stored in the cloud for:
                    
                    ✅ Backup & sync across devices
                    ✅ Personalized farming guidance  
                    ✅ Data recovery if you change phones
                    ✅ Works offline when needed
                    
                    We never share your personal farming information with third parties.
                    
                    Your data remains private and secure with Google Firebase.
                """.trimIndent())
                .setPositiveButton("I Understand") { dialog, which ->
                    // Mark privacy notice as shown
                    sharedPrefs.edit().putBoolean("privacy_notice_shown", true).apply()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun validateMobileNumber(mobile: String): Boolean {
        if (mobile.length != 10) {
            showError("Please enter a valid 10-digit mobile number")
            return false
        }
        return true
    }
    
    private fun checkIfUserExists(mobileNumberInput: String) {
        val formattedMobileForFirebase = "+91$mobileNumberInput"
        val currentFirebaseUser = auth.currentUser
        
        binding.mobileEntryLayout.progressBar.visibility = View.VISIBLE
        binding.mobileEntryLayout.btnContinue.isEnabled = false

        lifecycleScope.launch {
            try {
                // Scenario 1: Firebase user is authenticated AND matches the input mobile number
                if (currentFirebaseUser != null && currentFirebaseUser.phoneNumber == formattedMobileForFirebase) {
                    Log.d("AuthActivity", "Firebase user authenticated and matches input mobile: $formattedMobileForFirebase. Checking local DB.")
                    val localUser = userRepository.getUserByMobile(mobileNumberInput)
                    if (localUser != null) {
                        Log.d("AuthActivity", "User also found in local DB. Navigating to Main.")
                        navigateToMain()
                        return@launch // Skip further UI changes if navigating away
                    } else {
                        Log.w("AuthActivity", "Firebase authenticated user not in local DB. Proceed to profile creation.")
                        isExistingUser = false // Needs to create profile
                        existingUserData = null
                        // Mobile number is known and verified by Firebase
                        binding.mobileEntryLayout.etMobile.setText(mobileNumberInput) // Ensure it's set for prefill
                        setupUserDetailsForNewUser() // Show details form for profile creation
                        // No OTP needed as Firebase already verified this number session.
                        // However, current flow has Send OTP button in userDetailsLayout.
                        // For simplicity, we'll let it proceed to OTP if they click "Send OTP"
                        // or consider a direct save if Firebase auth is deemed sufficient.
                        // For now, let's assume they might want to re-verify or it's a new device.
                        // So, showing userDetailsLayout is appropriate.
                        binding.userDetailsLayout.tvUserStatus.text = "Complete your profile. Mobile number verified."
                    }
                } else {
                    // Scenario 2: No Firebase user / Firebase user does not match input mobile.
                    // This means we need to verify the entered mobile number via OTP.
                    // But first, check local DB to prefill details if user exists locally.
                    Log.d("AuthActivity", "No matching Firebase user. Checking local DB for mobile: $mobileNumberInput to prefill details.")
                    val localUser = userRepository.getUserByMobile(mobileNumberInput)
                    if (localUser != null) {
                        Log.d("AuthActivity", "User found in local DB. Prefilling details.")
                        isExistingUser = true
                        existingUserData = localUser
                        setupUserDetailsWithExistingData(localUser)
                    } else {
                        Log.d("AuthActivity", "New user (not in Firebase, not in local DB). Showing empty details form.")
                        isExistingUser = false
                        existingUserData = null
                        setupUserDetailsForNewUser()
                    }
                }

                // Transition to user details view (common for both sub-scenarios of Scenario 2, and for profile creation in Scenario 1)
                binding.mobileEntryLayout.root.visibility = View.GONE
                binding.userDetailsLayout.root.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("AuthActivity", "Error in checkIfUserExists: ${e.message}", e)
                showError("Error checking user data: ${e.message}")
            } finally {
                binding.mobileEntryLayout.progressBar.visibility = View.GONE
                binding.mobileEntryLayout.btnContinue.isEnabled = true
            }
        }
    }
    
    // tryFirestoreSyncForMobile method removed as it was a stub and Firestore sync is no longer used for user profiles.
    
    private fun setupUserDetailsWithExistingData(user: UserEntity) {
        // Show message that user exists
        binding.userDetailsLayout.tvUserStatus.visibility = View.VISIBLE
        binding.userDetailsLayout.tvUserStatus.text = "Welcome back! Please verify your details and proceed." // Updated message
        
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
        // Message updated for clarity, especially if mobile was pre-filled due to Firebase auth
        if (auth.currentUser != null && auth.currentUser?.phoneNumber?.replace("+91", "") == binding.mobileEntryLayout.etMobile.text.toString()) {
            binding.userDetailsLayout.tvUserStatus.text = "Mobile verified. Please complete your profile."
        } else {
            binding.userDetailsLayout.tvUserStatus.text = "New user? Please fill in your details."
        }
        
        // Clear all fields (except mobile if pre-filled from Firebase Auth)
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
                    binding.userDetailsLayout.etPincode.error = "Invalid PIN code format. Must start with 1-9."
                    // showError("PIN code must start with 1-9 followed by 5 digits") // Removed, rely on setError for field
                } else if (input.length < 6 && binding.userDetailsLayout.etPincode.error != null) {
                    // Clear error if user is correcting and length is no longer 6 to avoid persistent premature error
                    binding.userDetailsLayout.etPincode.error = null
                } else if (input.length == 6 && input.matches(pinCodePattern)) {
                    binding.userDetailsLayout.etPincode.error = null // Clear error on valid input
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
                    // Optionally show a global progress bar here if btnSendOtp is not the primary indicator
                }
                is AuthViewModel.AuthState.Success -> {
                    Log.d("AuthActivity", "ViewModel AuthState.Success: Navigating to MainActivity.")
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
        // Ensure this is called on the main thread if there's any doubt
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.d("AuthActivity", "Navigating to MainActivity.")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            Log.w("AuthActivity", "navigateToMain called from non-UI thread. Posting to main thread.")
            runOnUiThread {
                Log.d("AuthActivity", "Navigating to MainActivity (from non-UI thread).")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
    
    private fun showError(message: String) {
        Log.e("AuthActivity", "Displaying error: $message") // Keep Android Log for debugging
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}