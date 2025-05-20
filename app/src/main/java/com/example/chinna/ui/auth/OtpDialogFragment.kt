package com.example.chinna.ui.auth

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.chinna.databinding.DialogOtpBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class OtpDialogFragment : DialogFragment() {
    
    private lateinit var binding: DialogOtpBinding
    private var phoneNumber: String = ""
    private var onOtpEntered: ((String) -> Unit)? = null
    
    companion object {
        fun newInstance(
            phoneNumber: String,
            onOtpEntered: (String) -> Unit
        ): OtpDialogFragment {
            return OtpDialogFragment().apply {
                this.phoneNumber = phoneNumber
                this.onOtpEntered = onOtpEntered
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogOtpBinding.inflate(layoutInflater)
        
        binding.tvPhoneNumber.text = "OTP sent to +91$phoneNumber"
        
        setupOtpInput()
        
        binding.btnVerify.setOnClickListener {
            val otp = collectOtp()
            if (otp.length == 6) {
                onOtpEntered?.invoke(otp)
                dismiss()
            }
        }
        
        // Use the themed dialog with proper styling
        val dialog = MaterialAlertDialogBuilder(requireContext(), com.example.chinna.R.style.Theme_Chinna_Dialog)
            .setView(binding.root)
            .create()
            
        // Ensure dialog has correct background color
        dialog.window?.setBackgroundDrawableResource(com.example.chinna.R.color.dark_background)
            
        return dialog
    }
    
    private fun setupOtpInput() {
        val editTexts = listOf(
            binding.etOtp1,
            binding.etOtp2,
            binding.etOtp3,
            binding.etOtp4,
            binding.etOtp5,
            binding.etOtp6
        )
        
        editTexts.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        if (index < editTexts.size - 1) {
                            editTexts[index + 1].requestFocus()
                        }
                    }
                }
            })
        }
    }
    
    private fun collectOtp(): String {
        return buildString {
            append(binding.etOtp1.text)
            append(binding.etOtp2.text)
            append(binding.etOtp3.text)
            append(binding.etOtp4.text)
            append(binding.etOtp5.text)
            append(binding.etOtp6.text)
        }
    }
}