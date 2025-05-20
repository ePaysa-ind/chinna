package com.example.chinna.ui.auth

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chinna.data.remote.AuthService
import com.example.chinna.databinding.FragmentLoginBinding
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {
    
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var authService: AuthService
    
    private var selectedSowingDate: Long = 0L
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDropdowns()
        setupDatePicker()
        setupClickListeners()
    }
    
    private fun setupDropdowns() {
        // Setup crop dropdown
        val crops = listOf("", "Cotton", "Rice", "Wheat", "Maize", "Soybean", "Chilli", "Tomato", "Okra")
        val cropAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, crops)
        binding.etCrop.setAdapter(cropAdapter)
        
        // Setup soil type dropdown
        val soilTypes = listOf("Black", "Red", "Sandy loam")
        val soilAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, soilTypes)
        binding.etSoilType.setAdapter(soilAdapter)
    }
    
    private fun setupDatePicker() {
        binding.etSowingDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedSowingDate = calendar.timeInMillis
                    
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    binding.etSowingDate.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            
            // Set max date to today
            datePicker.datePicker.maxDate = System.currentTimeMillis()
            datePicker.show()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSendOtp.setOnClickListener {
            if (validateInputs()) {
                val phoneNumber = binding.etPhoneNumber.text.toString().trim()
                val name = binding.etName.text.toString().trim()
                val village = binding.etVillage.text.toString().trim()
                val acreage = binding.etAcreage.text.toString().toDoubleOrNull() ?: 0.0
                val crop = binding.etCrop.text.toString().trim()
                val soilType = binding.etSoilType.text.toString().trim()
                
                // Store data temporarily
                val sharedPref = requireActivity().getSharedPreferences("login_data", android.content.Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("name", name)
                    putString("village", village)
                    putFloat("acreage", acreage.toFloat())
                    putString("crop", crop)
                    putString("soilType", soilType)
                    putLong("sowingDate", selectedSowingDate)
                    apply()
                }
                
                sendOtp("+91$phoneNumber")
            }
        }
    }
    
    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val village = binding.etVillage.text.toString().trim()
        val acreage = binding.etAcreage.text.toString().trim()
        val soilType = binding.etSoilType.text.toString().trim()
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        
        when {
            name.isEmpty() -> {
                binding.etName.error = "Please enter your name"
                return false
            }
            village.isEmpty() -> {
                binding.etVillage.error = "Please enter your village"
                return false
            }
            acreage.isEmpty() -> {
                binding.etAcreage.error = "Please enter acreage"
                return false
            }
            acreage.toDoubleOrNull() == null || acreage.toDouble() <= 0 -> {
                binding.etAcreage.error = "Please enter valid acreage"
                return false
            }
            soilType.isEmpty() -> {
                binding.etSoilType.error = "Please select soil type"
                return false
            }
            phoneNumber.length != 10 -> {
                binding.etPhoneNumber.error = "Enter 10 digit number"
                return false
            }
        }
        
        // If crop is selected, sowing date must be selected
        val crop = binding.etCrop.text.toString().trim()
        if (crop.isNotEmpty() && selectedSowingDate == 0L) {
            Toast.makeText(context, "Please select sowing date for the crop", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }
    
    private fun sendOtp(phoneNumber: String) {
        authService.sendOtp(phoneNumber, requireActivity())
            .onEach { state ->
                when (state) {
                    is AuthService.AuthState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnSendOtp.isEnabled = false
                    }
                    is AuthService.AuthState.CodeSent -> {
                        binding.progressBar.visibility = View.GONE
                        val action = LoginFragmentDirections.actionLoginToOtp(
                            phoneNumber = phoneNumber,
                            verificationId = state.verificationId
                        )
                        findNavController().navigate(action)
                    }
                    is AuthService.AuthState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(LoginFragmentDirections.actionLoginToHome())
                    }
                    is AuthService.AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSendOtp.isEnabled = true
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
            .launchIn(lifecycleScope)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}