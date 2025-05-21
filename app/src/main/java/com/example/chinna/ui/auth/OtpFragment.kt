package com.example.chinna.ui.auth

import android.content.DialogInterface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.chinna.R
import com.example.chinna.data.remote.AuthService
import com.example.chinna.data.repository.UserRepository
import com.example.chinna.databinding.FragmentOtpBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OtpFragment : Fragment() {
    
    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!
    
    private val args: OtpFragmentArgs by navArgs()
    
    @Inject
    lateinit var authService: AuthService
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private var resendTimer: CountDownTimer? = null
    
    // Track number of OTP verification attempts
    private var otpAttempts = 0
    private val MAX_OTP_ATTEMPTS = 3  // Maximum allowed attempts before returning to phone input
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Restore attempts count if saved
        savedInstanceState?.let {
            otpAttempts = it.getInt("otp_attempts", 0)
        }
        
        setupUI()
        setupClickListeners()
        startResendTimer()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the attempts count on configuration changes
        outState.putInt("otp_attempts", otpAttempts)
    }
    
    private fun setupUI() {
        binding.tvOtpSentTo.text = getString(R.string.otp_sent_to, args.phoneNumber)
        
        // Show remaining attempts
        updateAttemptsUI()
    }
    
    private fun updateAttemptsUI() {
        val remainingAttempts = MAX_OTP_ATTEMPTS - otpAttempts
        if (remainingAttempts > 0) {
            binding.tvAttemptsRemaining.visibility = View.VISIBLE
            binding.tvAttemptsRemaining.text = "Attempts remaining: $remainingAttempts"
        } else {
            binding.tvAttemptsRemaining.visibility = View.GONE
        }
    }
    
    private fun setupClickListeners() {
        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            
            if (otp.length != 6) {
                binding.etOtp.error = getString(R.string.error_invalid_otp)
                return@setOnClickListener
            }
            
            verifyOtp(otp)
        }
        
        binding.btnResendOtp.setOnClickListener {
            resendOtp()
        }
        
        binding.btnChangeNumber.setOnClickListener {
            navigateBackToPhoneInput()
        }
    }
    
    private fun startResendTimer() {
        binding.btnResendOtp.isEnabled = false
        
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnResendOtp.text = "Resend in ${seconds}s"
            }
            
            override fun onFinish() {
                binding.btnResendOtp.text = getString(R.string.resend_otp)
                binding.btnResendOtp.isEnabled = true
            }
        }.start()
    }
    
    private fun verifyOtp(otp: String) {
        authService.verifyOtp(args.verificationId, otp)
            .onEach { state ->
                when (state) {
                    is AuthService.AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnVerify.isEnabled = false
                    }
                    is AuthService.AuthState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        saveUserData()
                    }
                    is AuthService.AuthState.Error -> {
                        handleVerificationError(state.message)
                    }
                    else -> {}
                }
            }
            .launchIn(lifecycleScope)
    }
    
    private fun handleVerificationError(errorMessage: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnVerify.isEnabled = true
        
        // Increment attempt counter
        otpAttempts++
        
        // Clear OTP input field for better UX
        binding.etOtp.text?.clear()
        
        // Show error message
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        
        // Update attempts UI
        updateAttemptsUI()
        
        // Check if maximum attempts reached
        if (otpAttempts >= MAX_OTP_ATTEMPTS) {
            showMaxAttemptsReachedDialog()
        }
    }
    
    private fun showMaxAttemptsReachedDialog() {
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Verification Failed")
                .setMessage("You've made $MAX_OTP_ATTEMPTS incorrect attempts. Would you like to try with a different phone number or request a new OTP?")
                .setPositiveButton("Try Different Number") { _, _ ->
                    navigateBackToPhoneInput()
                }
                .setNegativeButton("Request New OTP") { _, _ ->
                    // Reset attempts counter and request new OTP
                    otpAttempts = 0
                    updateAttemptsUI()
                    resendOtp()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun navigateBackToPhoneInput() {
        findNavController().popBackStack()
    }
    
    private fun resendOtp() {
        // Reset attempts counter when requesting new OTP
        otpAttempts = 0
        updateAttemptsUI()
        
        authService.sendOtp(args.phoneNumber, requireActivity())
            .onEach { state ->
                when (state) {
                    is AuthService.AuthState.Loading -> {
                        binding.btnResendOtp.isEnabled = false
                    }
                    is AuthService.AuthState.CodeSent -> {
                        Toast.makeText(context, "OTP resent", Toast.LENGTH_SHORT).show()
                        startResendTimer()
                    }
                    is AuthService.AuthState.Error -> {
                        binding.btnResendOtp.isEnabled = true
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
            .launchIn(lifecycleScope)
    }
    
    private fun navigateToHome() {
        try {
            findNavController().navigate(R.id.action_otp_to_home)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback navigation
            requireActivity().startActivity(
                android.content.Intent(requireContext(), com.example.chinna.ui.MainActivity::class.java)
            )
            requireActivity().finish()
        }
    }
    
    private fun saveUserData() {
        // Get stored data from login fragment
        val sharedPref = requireActivity().getSharedPreferences("login_data", android.content.Context.MODE_PRIVATE)
        val name = sharedPref.getString("name", "") ?: ""
        val pinCode = sharedPref.getString("pinCode", "") ?: ""
        val acreage = sharedPref.getFloat("acreage", 0f).toDouble()
        val crop = sharedPref.getString("crop", "") ?: ""
        val soilType = sharedPref.getString("soilType", "") ?: ""
        val sowingDate = sharedPref.getLong("sowingDate", 0L)
        
        lifecycleScope.launch {
            try {
                userRepository.saveUser(
                    mobile = args.phoneNumber.replace("+91", ""),
                    name = name,
                    pinCode = pinCode,
                    acreage = acreage,
                    crop = crop,
                    sowingDate = sowingDate,
                    soilType = soilType
                )
                
                // Clear temporary data
                sharedPref.edit().clear().apply()
                
                requireActivity().runOnUiThread {
                    Toast.makeText(context, getString(R.string.welcome_user, name), Toast.LENGTH_SHORT).show()
                    navigateToHome()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Error saving user data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        resendTimer?.cancel()
        _binding = null
    }
}