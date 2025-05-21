package com.example.chinna.ui.practices

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.chinna.R
import com.example.chinna.databinding.FragmentPracticesBinding
import com.example.chinna.model.Crop
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.chinna.data.repository.UserRepository
import javax.inject.Inject

@AndroidEntryPoint
class PracticesFragment : Fragment() {
    
    private var _binding: FragmentPracticesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var cropAdapter: CropAdapter
    private val crops = mutableListOf<Crop>()
    
    @Inject
    lateinit var userRepository: UserRepository
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        loadCrops()
    }
    
    private fun setupRecyclerView() {
        cropAdapter = CropAdapter { crop ->
            showCropPracticesDialog(crop)
        }
        
        binding.cropsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = cropAdapter
        }
    }
    
    private fun loadCrops() {
        try {
            val json = requireContext().assets.open("crops_data.json")
                .bufferedReader()
                .use { it.readText() }
                
            val jsonObject = Gson().fromJson(json, JsonObject::class.java)
            val cropsArray = jsonObject.getAsJsonArray("crops")
            
            cropsArray.forEach { element ->
                val cropObj = element.asJsonObject
                crops.add(
                    Crop(
                        id = cropObj.get("id").asString,
                        name = cropObj.get("name").asString,
                        localName = cropObj.get("localName").asString,
                        iconRes = getIconForCrop(cropObj.get("id").asString),
                        specificIconRes = getIconForCrop(cropObj.get("id").asString),
                        description = cropObj.get("description").asString
                    )
                )
            }
            
            cropAdapter.submitList(crops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getIconForCrop(cropId: String): Int {
        return when (cropId) {
            "okra" -> com.example.chinna.R.drawable.ic_crop_okra
            "chilli" -> com.example.chinna.R.drawable.ic_crop_chilli
            "tomato" -> com.example.chinna.R.drawable.ic_crop_tomato
            "rice" -> com.example.chinna.R.drawable.ic_crop_rice
            "wheat" -> com.example.chinna.R.drawable.ic_crop_wheat
            "cotton" -> com.example.chinna.R.drawable.ic_cotton
            "maize" -> com.example.chinna.R.drawable.ic_maize
            "soybean" -> com.example.chinna.R.drawable.ic_soybean
            else -> com.example.chinna.R.drawable.ic_crop
        }
    }
    
    private fun showCropPracticesDialog(crop: Crop) {
        // Skip the user input dialog and directly show practices
        // Get user data from repository instead of asking again
        lifecycleScope.launch {
            val currentUser = userRepository.getCurrentUserSync()
            
            currentUser?.let { user ->
                // Format the sowing date (use current date if not available)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sowingDate = if (user.sowingDate > 0) {
                    dateFormat.format(Date(user.sowingDate))
                } else {
                    // If user doesn't have sowing date, use a default date (15 days ago)
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_MONTH, -15)
                    dateFormat.format(calendar.time)
                }
                
                // Save the selected crop
                saveSelectedCrop(crop)
                
                // Show the practices directly
                showActualPractices(crop, sowingDate)
            } ?: run {
                // If no user data available (shouldn't happen), use default values
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_MONTH, -15)
                val defaultSowingDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
                
                // Save the selected crop
                saveSelectedCrop(crop)
                
                // Show practices with default date
                showActualPractices(crop, defaultSowingDate)
            }
        }
    }
    
    // NOTE: This method is no longer used since we're skipping the user input dialog.
    // Kept for reference in case it's needed in the future.
    private fun showUserInputDialog(crop: Crop) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_input, null)
        
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Required Information")
            .setView(dialogView)
            .setPositiveButton("Show Practices", null)
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
        
        // Get references to input fields
        val nameInput = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_name)
        val villageInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_pincode)
        val acreageInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_acreage)
        val sowingDateInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_sowing_date)
        
        // Pre-fill with user data from database
        lifecycleScope.launch {
            val currentUser = userRepository.getCurrentUserSync()
            
            currentUser?.let { user ->
                requireActivity().runOnUiThread {
                    nameInput.setText(user.name)
                    villageInput.setText(user.pinCode)
                    acreageInput.setText(user.acreage.toString())
                    
                    // Format and set sowing date if available
                    if (user.sowingDate > 0) {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        sowingDateInput.setText(dateFormat.format(Date(user.sowingDate)))
                    }
                }
            }
        }
        
        // Set up village input to only accept letters and spaces and validate length in real-time
        villageInput.filters = arrayOf(android.text.InputFilter { source, start, end, _, _, _ ->
            for (i in start until end) {
                if (!source[i].isLetter() && !source[i].isWhitespace()) {
                    return@InputFilter ""
                }
            }
            null
        })
        
        // Add TextWatcher for real-time validation of village name
        villageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val village = s.toString().trim()
                if (village.length < 3 && village.isNotEmpty()) {
                    villageInput.error = "Village name must be at least 3 characters"
                } else if (!isVillageValid(village) && village.isNotEmpty()) {
                    villageInput.error = "Village name cannot have numbers. Only letters allowed"
                } else {
                    villageInput.error = null
                }
            }
        })
        
        // Set up acreage input to only accept values 1-9, not 0
        acreageInput.filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
            val input = source.subSequence(start, end).toString()
            if (input.isEmpty()) return@InputFilter null
            
            val finalValue = (dest.subSequence(0, dstart).toString() + input + 
                              dest.subSequence(dend, dest.length).toString()).toIntOrNull() ?: return@InputFilter ""
            
            if (finalValue == 0 || finalValue > 9) {
                return@InputFilter ""
            }
            null
        })
        
        // Add TextWatcher for real-time validation of acreage
        acreageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val acreage = s.toString().trim()
                if (acreage.isNotEmpty()) {
                    val acreageValue = acreage.toIntOrNull()
                    if (acreageValue == null || acreageValue > 9 || acreageValue == 0) {
                        acreageInput.error = "Acreage must be 1-9"
                    } else {
                        acreageInput.error = null
                    }
                }
            }
        })
        
        // Set up ime options for proper navigation
        villageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                acreageInput.requestFocus()
                true
            } else {
                false
            }
        }
        
        // Check if we already have the user's name saved
        val prefs = requireContext().getSharedPreferences("farmer_details", Context.MODE_PRIVATE)
        val savedName = prefs.getString("name", "") ?: ""
        if (savedName.isNotEmpty()) {
            nameInput.setText(savedName)
            nameInput.isEnabled = false // User already logged in, disable name field
        } else {
            // Set up autocomplete for names
            val farmerNames = getSavedFarmerNames()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, farmerNames)
            nameInput.setAdapter(adapter)
        }
        
        // Make sowing date clickable for date picker
        sowingDateInput.isFocusable = false
        sowingDateInput.isClickable = true
        sowingDateInput.setOnClickListener {
            showDatePicker { date ->
                sowingDateInput.setText(date)
            }
        }
        
        // Override the positive button click to add validation
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val village = villageInput.text.toString().trim()
            val acreage = acreageInput.text.toString().trim()
            val sowingDate = sowingDateInput.text.toString().trim()
            
            // Validate all fields are filled
            when {
                name.isEmpty() -> {
                    nameInput.error = "Name is required"
                }
                !isNameValid(name) -> {
                    nameInput.error = "Name must have at least 3 alphabets and no numbers"
                }
                village.isEmpty() -> {
                    villageInput.error = "Village is required"
                }
                !isVillageValid(village) -> {
                    villageInput.error = "Village name cannot have numbers. Only letters allowed"
                }
                acreage.isEmpty() -> {
                    acreageInput.error = "Acreage is required"
                }
                acreage.toIntOrNull() == null || acreage.toInt() > 9 || acreage.toInt() == 0 -> {
                    acreageInput.error = "Acreage must be 1-9"
                }
                sowingDate.isEmpty() -> {
                    sowingDateInput.error = "Sowing date is required"
                }
                !isDateValid(sowingDate) -> {
                    sowingDateInput.error = "Date must be at least 15 days before today"
                }
                else -> {
                    // All fields are filled, proceed
                    saveUserData(name, village, acreage, sowingDate)
                    saveFarmerName(name)
                    saveSelectedCrop(crop)  // Save the selected crop
                    showActualPractices(crop, sowingDate)
                    dialog.dismiss()
                }
            }
        }
    }
    
    private fun getSavedFarmerNames(): List<String> {
        val prefs = requireContext().getSharedPreferences("farmer_names", Context.MODE_PRIVATE)
        val namesSet = prefs.getStringSet("names", setOf()) ?: setOf()
        return namesSet.toList()
    }
    
    private fun saveFarmerName(name: String) {
        val prefs = requireContext().getSharedPreferences("farmer_names", Context.MODE_PRIVATE)
        val namesSet = prefs.getStringSet("names", setOf())?.toMutableSet() ?: mutableSetOf()
        namesSet.add(name)
        prefs.edit().putStringSet("names", namesSet).apply()
    }
    
    private fun saveUserData(name: String, pinCode: String, acreage: String, sowingDate: String) {
        // Save to preferences
        val prefs = requireContext().getSharedPreferences("farmer_details", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("name", name)
            putString("pinCode", pinCode)
            putString("acreage", acreage)
            putString("sowing_date", sowingDate)
            apply()
        }
    }
    
    private fun saveSelectedCrop(crop: Crop) {
        // Save selected crop information
        val prefs = requireContext().getSharedPreferences("selected_crop", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("crop_id", crop.id)
            putString("crop_name", crop.name)
            putString("crop_local_name", crop.localName)
            apply()
        }
    }
    
    private fun showActualPractices(crop: Crop, sowingDate: String) {
        try {
            val json = requireContext().assets.open("crops_data.json")
                .bufferedReader()
                .use { it.readText() }
                
            val jsonObject = Gson().fromJson(json, JsonObject::class.java)
            val cropsArray = jsonObject.getAsJsonArray("crops")
            
            cropsArray.forEach { element ->
                val cropObj = element.asJsonObject
                if (cropObj.get("id").asString == crop.id) {
                    val practicesArray = cropObj.getAsJsonArray("practices")
                    // Navigate to landscape view with sowing date
                    navigateToLandscapeView(crop, practicesArray, sowingDate)
                    return@forEach
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun navigateToLandscapeView(crop: Crop, practicesArray: JsonArray, sowingDate: String) {
        val action = PracticesFragmentDirections.actionPracticesToSummary(
            crop = crop,
            practices = practicesArray.toString(),
            sowingDate = sowingDate
        )
        findNavController().navigate(action)
    }
    
    private fun displayPractices(crop: Crop, practicesArray: JsonArray) {
        // Group practices by 2-week intervals for better readability
        val intervals = mutableMapOf<String, MutableList<String>>()
        
        practicesArray.forEach { element ->
            val practiceObj = element.asJsonObject
            val weekNumber = practiceObj.get("weekNumber").asInt
            val title = practiceObj.get("title").asString
            val activities = practiceObj.getAsJsonArray("activities")
            
            // Determine interval (weeks 1-2, 3-4, etc.)
            val intervalKey = when (weekNumber) {
                in 1..2 -> "Weeks 1-2"
                in 3..4 -> "Weeks 3-4"
                in 5..6 -> "Weeks 5-6"
                in 7..8 -> "Weeks 7-8"
                in 9..10 -> "Weeks 9-10"
                in 11..12 -> "Weeks 11-12"
                in 13..14 -> "Weeks 13-14"
                else -> "After Week 14"
            }
            
            // Build practice details
            val practiceDetails = StringBuilder()
            practiceDetails.append("Week $weekNumber - $title\n")
            activities.forEach { activity ->
                practiceDetails.append("• ${activity.asString}\n")
            }
            
            val reminder = practiceObj.get("criticalReminder")?.asString
            if (reminder != null) {
                practiceDetails.append("⚠️ $reminder\n")
            }
            
            intervals.getOrPut(intervalKey) { mutableListOf() }.add(practiceDetails.toString())
        }
        
        // Create dialog view
        val dialogView = layoutInflater.inflate(R.layout.dialog_practices_summary, null)
        val practicesContainer = dialogView.findViewById<LinearLayout>(R.id.practices_container)
        val titleText = dialogView.findViewById<TextView>(R.id.title_text)
        
        titleText.text = "${crop.name} (${crop.localName}) - Package of Practices"
        
        // Sort intervals and display
        val sortedIntervals = intervals.toSortedMap { a, b ->
            // Extract week numbers for proper sorting
            val aWeek = a.split("-")[0].filter { it.isDigit() }.toIntOrNull() ?: 999
            val bWeek = b.split("-")[0].filter { it.isDigit() }.toIntOrNull() ?: 999
            aWeek.compareTo(bWeek)
        }
        
        sortedIntervals.forEach { (interval, practicesList) ->
            // Add interval header
            val intervalHeader = TextView(context).apply {
                text = interval
                textSize = 20f
                setPadding(0, 16, 0, 8)
                setTextColor(context.getColor(R.color.dark_accent))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            practicesContainer.addView(intervalHeader)
            
            // Add practices for this interval
            practicesList.forEach { practice ->
                val practiceText = TextView(context).apply {
                    text = practice
                    textSize = 16f
                    setPadding(8, 4, 8, 12)
                    setTextColor(context.getColor(R.color.dark_text_primary))
                }
                practicesContainer.addView(practiceText)
            }
        }
        
        // Show dialog
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        // Set date constraints - max date is 15 days before today
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        val fifteenDaysAgo = today - (15 * 24 * 60 * 60 * 1000L) // 15 days in milliseconds
        
        val constraintsBuilder = CalendarConstraints.Builder()
            .setEnd(fifteenDaysAgo) // Maximum date is 15 days ago
        
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Sowing Date")
            .setSelection(fifteenDaysAgo) // Default to 15 days ago
            .setCalendarConstraints(constraintsBuilder.build())
            .build()
            
        datePicker.addOnPositiveButtonClickListener { selection ->
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = Date(selection)
            onDateSelected(dateFormat.format(date))
        }
        
        datePicker.show(childFragmentManager, "DATE_PICKER")
    }
    
    private fun isDateValid(dateString: String): Boolean {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val sowingDate = dateFormat.parse(dateString) ?: return false
            val today = Date()
            val diffInMillis = today.time - sowingDate.time
            val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)
            // Date must be at least 15 days ago (in the past)
            return diffInDays >= 15
        } catch (e: Exception) {
            return false
        }
    }
    
    private fun isNameValid(name: String): Boolean {
        // Name must have at least 3 alphabets and no numbers
        val alphabetCount = name.count { it.isLetter() }
        val hasNumbers = name.any { it.isDigit() }
        return alphabetCount >= 3 && !hasNumbers
    }
    
    private fun isVillageValid(village: String): Boolean {
        // Village name must only contain alphabets and spaces
        return village.all { it.isLetter() || it.isWhitespace() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}