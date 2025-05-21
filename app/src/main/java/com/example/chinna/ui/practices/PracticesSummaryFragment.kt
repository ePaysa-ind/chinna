package com.example.chinna.ui.practices

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.chinna.R
import com.example.chinna.data.remote.WeatherService
import com.example.chinna.data.remote.GeminiService
import com.example.chinna.data.remote.WeatherData
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.databinding.FragmentPracticesSummaryBinding
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class PracticesSummaryFragment : Fragment() {
    
    @Inject
    lateinit var weatherService: WeatherService
    
    @Inject
    lateinit var geminiService: GeminiService
    
    @Inject
    lateinit var prefsManager: PrefsManager
    
    @Inject
    lateinit var userRepository: com.example.chinna.data.repository.UserRepository
    
    private var _binding: FragmentPracticesSummaryBinding? = null
    private val binding get() = _binding!!
    private val args: PracticesSummaryFragmentArgs by navArgs()
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            loadWeatherData()
        } else {
            // Permission was denied, show message to user
            showLocationPermissionDeniedMessage()
            // Fallback to default weather with notification
            showDefaultWeather(isPermissionDenied = true)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use the updated layout file
        _binding = FragmentPracticesSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        loadSummaryData()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // Load crop icon from assets
        try {
            val iconStream = requireContext().assets.open("${args.crop.id}.png")
            val drawable = android.graphics.drawable.Drawable.createFromStream(iconStream, null)
            binding.cropIcon.setImageDrawable(drawable)
        } catch (e: Exception) {
            // Fallback to default icon
            binding.cropIcon.setImageResource(args.crop.iconRes)
        }
        
        binding.cropName.text = args.crop.name
        // Don't show local name - removed from layout
        
        // Check location permission and load weather
        checkLocationPermission()
        
        // Calculate days since sowing
        val daysSinceSowing = calculateDaysSinceSowing(args.sowingDate)
        val currentStage = getCurrentGrowthStage(daysSinceSowing)
        val progress = calculateProgress(daysSinceSowing)
        
        // Set progress for growth stage
        binding.growthProgressBar.progress = progress
    }
    
    private fun loadSummaryData() {
        val practicesArray = Gson().fromJson(args.practices, JsonArray::class.java)
        val daysSinceSowing = calculateDaysSinceSowing(args.sowingDate)
        val currentWeek = (daysSinceSowing / 7) + 1
        
        // Find current week's practices with detailed information
        var currentWeekTasks = mutableListOf<Pair<String, String>>()
        var criticalReminders = mutableListOf<String>()
        var hasCurrentWeekTasks = false
        
        practicesArray.forEach { element ->
            val practiceObj = element.asJsonObject
            val weekNumber = practiceObj.get("weekNumber").asInt
            val title = practiceObj.get("title").asString
            val activities = practiceObj.getAsJsonArray("activities")
            val criticalReminder = practiceObj.get("criticalReminder")?.asString
            
            when {
                weekNumber == currentWeek -> {
                    hasCurrentWeekTasks = true
                    activities.forEach { activity ->
                        currentWeekTasks.add(Pair(activity.asString, title))
                    }
                    criticalReminder?.let { criticalReminders.add(it) }
                }
            }
        }
        
        // If no tasks for current week, check nearest week
        if (!hasCurrentWeekTasks) {
            val nearestWeek = practicesArray.minByOrNull { element ->
                val weekNumber = element.asJsonObject.get("weekNumber").asInt
                kotlin.math.abs(weekNumber - currentWeek)
            }?.asJsonObject
            
            nearestWeek?.let { practiceObj ->
                val title = practiceObj.get("title").asString
                val activities = practiceObj.getAsJsonArray("activities")
                activities.forEach { activity ->
                    currentWeekTasks.add(Pair(activity.asString, title))
                }
            }
        }
        
        // Check if this is a new crop (for sowing guidance) or an existing crop
        lifecycleScope.launch {
            try {
                val userData = userRepository.getCurrentUserSync()
                val userCrop = userData?.crop ?: ""
                val isNewCrop = !args.crop.name.equals(userCrop, ignoreCase = true)
                
                // Set critical alerts for weather-based risks from API
                if (criticalReminders.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        binding.alertCard.visibility = View.VISIBLE
                        binding.alertText.text = criticalReminders.joinToString("\n")
                    }
                } else {
                    // If no critical reminders from practices data, try to get from Gemini API
                    val weatherData = weatherService.getCurrentWeather(userData?.pinCode ?: "")
                    if (weatherData != null) {
                        val context = mapOf<String, String>(
                            "stage" to getCurrentGrowthStage(daysSinceSowing),
                            "weather" to weatherData.condition,
                            "temperature" to "${weatherData.temperature}°C",
                            "humidity" to "${weatherData.humidity}%",
                            "rainChance" to "${weatherData.rainChance}%"
                        )
                        
                        // Get critical alert from Gemini if current conditions warrant it
                        val criticalPrompt = "IF URGENT, provide critical warning for ${args.crop.name} farmers TODAY. IF NOT URGENT, respond with 'NO_ALERT'"
                        val criticalAlert = geminiService.getAdvice(criticalPrompt, args.crop.name, context)
                        
                        if (criticalAlert.isNotEmpty() && !criticalAlert.contains("NO_ALERT", ignoreCase = true)) {
                            requireActivity().runOnUiThread {
                                binding.alertCard.visibility = View.VISIBLE
                                binding.alertText.text = criticalAlert
                            }
                        } else {
                            requireActivity().runOnUiThread {
                                binding.alertCard.visibility = View.GONE
                            }
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            binding.alertCard.visibility = View.GONE
                        }
                    }
                }
                
                // Generate package of practices based on API data
                if (isNewCrop) {
                    // For a new crop, focus on sowing advice
                    generateSowingPackageOfPractices()
                } else {
                    // For existing crop, use current week practices
                    generateMaintenancePackageOfPractices(currentWeekTasks)
                }
                
                // Generate pest prevention advice based on weather conditions
                generatePestPreventionAdvice()
                
                // Update quick stats from API
                updateQuickStats()
                
                // Load weather data
                checkLocationPermission()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("PracticesSummaryFragment", "Error loading summary data: ${e.message}")
                
                // Fallbacks for key components if API calls fail
                requireActivity().runOnUiThread {
                    binding.alertCard.visibility = View.GONE
                    binding.weatherText.text = "Weather data unavailable"
                }
            }
        }
        
        // Set initial loading states
        binding.weatherIcon.setImageResource(R.drawable.ic_sun)
        binding.weatherText.text = "Loading weather..."
        binding.practiceTask1.text = "• Loading recommendations..."
        binding.practiceTask2.text = ""
        binding.practiceTask3.text = ""
    }
    
    /**
     * Generate sowing guidance for the first two weeks after sowing
     * This will be fetched from Gemini API based on crop, region, and conditions
     */
    private suspend fun generateSowingPackageOfPractices() {
        try {
            // Set loading state
            requireActivity().runOnUiThread {
                binding.practiceTask1.text = "• Loading recommendations..."
                binding.practiceTask2.text = ""
                binding.practiceTask3.text = ""
            }
            
            // Get user data for location
            val userData = userRepository.getCurrentUserSync()
            val pinCode = userData?.pinCode ?: ""
            val soilType = userData?.soilType ?: "unknown"
            
            // Get weather data for recommendations
            val weatherData = weatherService.getCurrentWeather(pinCode)
            
            // Request sowing practices from Gemini API
            val context = mapOf<String, String>(
                "stage" to "sowing",
                "concern" to "planting",
                "weather" to (weatherData?.condition ?: "unknown"),
                "temperature" to "${weatherData?.temperature ?: "--"}°C",
                "humidity" to "${weatherData?.humidity ?: "--"}%",
                "rainChance" to "${weatherData?.rainChance ?: "--"}%",
                "soilType" to soilType,
                "pinCode" to pinCode,
                "region" to "India"
            )
            
            // Get specific practice recommendations from Gemini
            val sowingAdvice = geminiService.getSowingPractices(
                crop = args.crop.name,
                pinCode = pinCode,
                soilType = soilType,
                weatherData = weatherData
            )
            
            // Update UI with API response
            requireActivity().runOnUiThread {
                if (sowingAdvice.isNotEmpty()) {
                    binding.practiceTask1.text = "• ${sowingAdvice.getOrNull(0) ?: ""}"
                    binding.practiceTask2.text = "• ${sowingAdvice.getOrNull(1) ?: ""}"
                    binding.practiceTask3.text = "• ${sowingAdvice.getOrNull(2) ?: ""}"
                } else {
                    // Handle API failure - use Gemini general advice as fallback
                    val prompt = "Give 3 specific sowing steps for ${args.crop.name} with exact spacing measurements"
                    lifecycleScope.launch {
                        val fallbackAdvice = geminiService.getAdvice(prompt, args.crop.name, context)
                        val advicePoints = fallbackAdvice.split(".").filter { it.trim().isNotEmpty() }.take(3)
                        
                        requireActivity().runOnUiThread {
                            binding.practiceTask1.text = "• ${advicePoints.getOrNull(0)?.trim() ?: ""}"
                            binding.practiceTask2.text = "• ${advicePoints.getOrNull(1)?.trim() ?: ""}"
                            binding.practiceTask3.text = "• ${advicePoints.getOrNull(2)?.trim() ?: ""}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PracticesSummaryFragment", "Error getting sowing practices: ${e.message}")
            
            // Handle error but still try to get some advice
            lifecycleScope.launch {
                try {
                    val fallbackPrompt = "3 simple steps for sowing ${args.crop.name}"
                    val fallbackAdvice = geminiService.getAdvice(
                        prompt = fallbackPrompt,
                        crop = args.crop.name,
                        context = mapOf<String, String>("stage" to "sowing", "concern" to "planting")
                    )
                    
                    val advicePoints = fallbackAdvice.split(".").filter { it.trim().isNotEmpty() }.take(3)
                    requireActivity().runOnUiThread {
                        binding.practiceTask1.text = "• ${advicePoints.getOrNull(0)?.trim() ?: ""}"
                        binding.practiceTask2.text = "• ${advicePoints.getOrNull(1)?.trim() ?: ""}"
                        binding.practiceTask3.text = "• ${advicePoints.getOrNull(2)?.trim() ?: ""}"
                    }
                } catch (e: Exception) {
                    Log.e("PracticesSummaryFragment", "Even fallback advice failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Generate maintenance guidance for existing crops based on API data
     */
    private suspend fun generateMaintenancePackageOfPractices(currentWeekTasks: List<Pair<String, String>>) {
        try {
            // Set loading state
            requireActivity().runOnUiThread {
                binding.practiceTask1.text = "• Loading recommendations..."
                binding.practiceTask2.text = ""
                binding.practiceTask3.text = ""
            }
            
            // Get user data for location and soil
            val userData = userRepository.getCurrentUserSync()
            val pinCode = userData?.pinCode ?: ""
            val soilType = userData?.soilType ?: "unknown"
            
            // Get current growth stage and days since sowing
            val daysSinceSowing = calculateDaysSinceSowing(args.sowingDate)
            val growthStage = getCurrentGrowthStage(daysSinceSowing)
            
            // Get weather data
            val weatherData = weatherService.getCurrentWeather(pinCode)
            
            // Try to use the API-provided weekly tasks first
            if (currentWeekTasks.isNotEmpty()) {
                val tasks = currentWeekTasks.map { it.first }.distinct()
                requireActivity().runOnUiThread {
                    binding.practiceTask1.text = "• ${tasks.getOrNull(0) ?: ""}"
                    binding.practiceTask2.text = "• ${tasks.getOrNull(1) ?: ""}"
                    binding.practiceTask3.text = "• ${tasks.getOrNull(2) ?: ""}"
                }
                return
            }
            
            // If no predefined tasks, request specific advice from Gemini based on current conditions
            val maintenanceAdvice = geminiService.getMaintenancePractices(
                crop = args.crop.name,
                growthStage = growthStage,
                daysSinceSowing = daysSinceSowing,
                pinCode = pinCode,
                soilType = soilType,
                weatherData = weatherData
            )
            
            // Update UI with API response
            requireActivity().runOnUiThread {
                if (maintenanceAdvice.isNotEmpty()) {
                    binding.practiceTask1.text = "• ${maintenanceAdvice.getOrNull(0) ?: ""}"
                    binding.practiceTask2.text = "• ${maintenanceAdvice.getOrNull(1) ?: ""}"
                    binding.practiceTask3.text = "• ${maintenanceAdvice.getOrNull(2) ?: ""}"
                } else {
                    // Handle API failure with a fallback Gemini call
                    val context = mapOf<String, String>(
                        "stage" to growthStage,
                        "daysSinceSowing" to daysSinceSowing.toString(),
                        "weather" to (weatherData?.condition ?: "unknown"),
                        "temperature" to "${weatherData?.temperature ?: "--"}°C",
                        "humidity" to "${weatherData?.humidity ?: "--"}%",
                        "rainChance" to "${weatherData?.rainChance ?: "--"}%",
                        "soilType" to soilType
                    )
                    
                    lifecycleScope.launch {
                        try {
                            val prompt = "3 specific maintenance tasks for ${args.crop.name} in ${growthStage} stage after ${daysSinceSowing} days"
                            val fallbackAdvice = geminiService.getAdvice(prompt, args.crop.name, context)
                            val advicePoints = fallbackAdvice.split(".").filter { it.trim().isNotEmpty() }.take(3)
                            
                            requireActivity().runOnUiThread {
                                binding.practiceTask1.text = "• ${advicePoints.getOrNull(0)?.trim() ?: ""}"
                                binding.practiceTask2.text = "• ${advicePoints.getOrNull(1)?.trim() ?: ""}"
                                binding.practiceTask3.text = "• ${advicePoints.getOrNull(2)?.trim() ?: ""}"
                            }
                        } catch (e: Exception) {
                            Log.e("PracticesSummaryFragment", "Fallback advice failed: ${e.message}")
                            requireActivity().runOnUiThread {
                                binding.practiceTask1.text = "• API unavailable"
                                binding.practiceTask2.text = "• "
                                binding.practiceTask3.text = "• "
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PracticesSummaryFragment", "Error getting maintenance practices: ${e.message}")
            
            requireActivity().runOnUiThread {
                binding.practiceTask1.text = "• API unavailable"
                binding.practiceTask2.text = "• "
                binding.practiceTask3.text = "• "
            }
        }
    }
    
    /**
     * Generate pest and disease prevention from Gemini API
     * based on current weather, location, crop stage, and soil type
     */
    private suspend fun generatePestPreventionAdvice() {
        try {
            // Set loading state
            requireActivity().runOnUiThread {
                binding.pestRiskText.text = "Checking pest and disease risks..."
                binding.preventionTask1.text = "• Loading recommendations..."
                binding.preventionTask2.text = ""
                binding.preventionTask3.text = ""
            }
            
            // Get user data for location and soil type
            val userData = userRepository.getCurrentUserSync()
            val pinCode = userData?.pinCode ?: ""
            val soilType = userData?.soilType ?: "unknown"
            
            // Get weather data for the user's location
            val weatherData = weatherService.getCurrentWeather(pinCode)
            
            // Get growth stage based on days since sowing
            val daysSinceSowing = calculateDaysSinceSowing(args.sowingDate)
            val growthStage = getCurrentGrowthStage(daysSinceSowing)
            
            // Get current month for seasonal risks
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-based month
            
            // Request specific pest and disease prevention advice from Gemini API
            val pestAdvice = geminiService.getPreemptivePestAdvice(
                crop = args.crop.name,
                growthStage = growthStage,
                weatherData = weatherData,
                soilType = soilType
            )
            
            // Get likely pests for this crop in current conditions
            val likelyPests = geminiService.getLikelyPests(
                crop = args.crop.name,
                growthStage = growthStage,
                daysSinceSowing = daysSinceSowing,
                weatherData = weatherData,
                pinCode = pinCode,
                soilType = soilType,
                month = currentMonth
            )
            
            // Update UI with API response data
            requireActivity().runOnUiThread {
                // Update likely pests text
                binding.pestRiskText.text = likelyPests.ifEmpty { "Checking for common seasonal pests" }
                binding.pestRiskText.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_text_primary))
                
                // Update prevention tasks
                if (pestAdvice.isNotEmpty()) {
                    binding.preventionTask1.text = "• ${pestAdvice.getOrNull(0) ?: ""}"
                    binding.preventionTask2.text = "• ${pestAdvice.getOrNull(1) ?: ""}"
                    binding.preventionTask3.text = "• ${pestAdvice.getOrNull(2) ?: ""}"
                } else {
                    // If API fails, try a more generic Gemini request
                    lifecycleScope.launch {
                        try {
                            val context = mapOf<String, String>(
                                "stage" to growthStage,
                                "daysSinceSowing" to daysSinceSowing.toString(),
                                "weather" to (weatherData?.condition ?: "unknown"),
                                "temperature" to "${weatherData?.temperature ?: "--"}°C",
                                "humidity" to "${weatherData?.humidity ?: "--"}%",
                                "rainChance" to "${weatherData?.rainChance ?: "--"}%",
                                "soilType" to soilType
                            )
                            
                            val prompt = "3 pest prevention steps for ${args.crop.name} in ${growthStage} stage with exact measurements"
                            val fallbackAdvice = geminiService.getAdvice(prompt, args.crop.name, context)
                            val advicePoints = fallbackAdvice.split(".").filter { it.trim().isNotEmpty() }.take(3)
                            
                            requireActivity().runOnUiThread {
                                binding.preventionTask1.text = "• ${advicePoints.getOrNull(0)?.trim() ?: ""}"
                                binding.preventionTask2.text = "• ${advicePoints.getOrNull(1)?.trim() ?: ""}"
                                binding.preventionTask3.text = "• ${advicePoints.getOrNull(2)?.trim() ?: ""}"
                            }
                        } catch (e: Exception) {
                            Log.e("PracticesSummaryFragment", "Fallback pest advice failed: ${e.message}")
                            requireActivity().runOnUiThread {
                                binding.preventionTask1.text = "• API unavailable"
                                binding.preventionTask2.text = "• "
                                binding.preventionTask3.text = "• "
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback in case of network or API error
            e.printStackTrace()
            Log.e("PracticesSummaryFragment", "Error getting pest advice: ${e.message}")
            
            requireActivity().runOnUiThread {
                binding.pestRiskText.text = "Check local agricultural office for pest alerts"
                binding.preventionTask1.text = "• API unavailable"
                binding.preventionTask2.text = "• "
                binding.preventionTask3.text = "• "
            }
        }
    }
    
    // This function has been replaced by generatePestPreventionAdvice(),
    // but we keep it as a no-op to avoid breaking existing code
    private fun loadWeatherRecommendations(scheduledTasks: List<Pair<String, String>>) {
        // No-op - functionality moved to generatePestPreventionAdvice()
    }
    
    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }
    
    private fun calculateDaysSinceSowing(sowingDate: String): Int {
        return try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val sowing = dateFormat.parse(sowingDate) ?: Date()
            val today = Date()
            val diffInMillis = today.time - sowing.time
            TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS).toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    private fun getCurrentGrowthStage(days: Int): String {
        return when (days) {
            in 0..14 -> "Seedling"
            in 15..35 -> "Early Growth"
            in 36..65 -> "Flowering"
            in 66..95 -> "Fruiting"
            else -> "Harvest Ready"
        }
    }
    
    private fun calculateProgress(days: Int): Int {
        return when {
            days <= 0 -> 0
            days >= 100 -> 100
            else -> days
        }
    }
    
    private fun getPracticesDuration(practicesArray: JsonArray): Int {
        var maxWeek = 0
        practicesArray.forEach { element ->
            val practiceObj = element.asJsonObject
            val weekNumber = practiceObj.get("weekNumber").asInt
            if (weekNumber > maxWeek) maxWeek = weekNumber
        }
        return maxWeek * 7
    }
    
    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadWeatherData()
            }
            else -> {
                // Check if we should show rationale
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Show explanation of why we need this permission
                    showLocationPermissionRationale()
                } else {
                    // Request permissions directly
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }
    }
    
    private fun showLocationPermissionRationale() {
        // Create alert dialog with SessionDialog theme which has BLACK text on WHITE background for readability
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_Chinna_SessionDialog)
            .setTitle("Location Permission Required")
            .setMessage("We need location permission to provide accurate weather data for your area. This helps us give better crop advice based on local conditions.")
            .setPositiveButton("GRANT PERMISSION") { _, _ ->
                // Request permissions
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("USE ESTIMATED DATA") { _, _ ->
                // Use default weather data
                showLocationPermissionDeniedMessage()
                showDefaultWeather(isPermissionDenied = true)
            }
        
        // Show the dialog
        val dialog = dialogBuilder.show()
        
        // Enhance text visibility
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextColor(Color.BLACK) // Ensure BLACK text
            textSize = 16f
        }
        
        // Style the title
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            setTextColor(Color.BLACK) // Ensure BLACK text
            textSize = 18f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        
        // Style the buttons with explicit colors for better visibility
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_primary)) // GREEN
        }
        
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(Color.BLACK) // BLACK
        }
        
        // Set dialog background to ensure white background
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
    }
    
    private fun loadWeatherData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show loading state
                binding.weatherText.text = "Loading weather data..."
                
                // Get user data for weather lookup
                val currentUserData = try {
                    userRepository.getCurrentUserSync()
                } catch (e: Exception) {
                    android.util.Log.e("PracticesSummaryFragment", "Error getting user data: ${e.message}")
                    null
                }
                
                val pinCode = currentUserData?.pinCode ?: ""
                
                // Log attempt to fetch weather
                android.util.Log.d("PracticesSummaryFragment", 
                    "Attempting to fetch weather data" + if (pinCode.isNotEmpty()) " for PIN code: $pinCode" else "")
                
                val weather = weatherService.getCurrentWeather(pinCode)
                if (weather != null) {
                    // Log successful weather fetch
                    android.util.Log.d("PracticesSummaryFragment", 
                        "Weather data fetched successfully: ${weather.condition}, " +
                        "${weather.temperature}°C, ${weather.humidity}%, ${weather.rainChance}% rain")
                    
                    updateWeatherUI(weather)
                } else {
                    // Log failed weather fetch
                    android.util.Log.w("PracticesSummaryFragment", 
                        "Weather fetch returned null, using default weather data")
                    
                    // Show default weather for Indian conditions
                    showDefaultWeather(isPermissionDenied = false)
                }
            } catch (e: Exception) {
                // Log exception
                android.util.Log.e("PracticesSummaryFragment", 
                    "Error fetching weather data: ${e.message}", e)
                
                e.printStackTrace()
                showDefaultWeather(isPermissionDenied = false)
            }
        }
    }
    
    private fun showLocationPermissionDeniedMessage() {
        // Show message using Snackbar
        val snackbar = com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "Location permission denied. Weather data will be estimated based on season.",
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        )
        
        // Add action to request permission again
        snackbar.setAction("Grant Permission") {
            checkLocationPermission()
        }
        
        // Set action button color 
        snackbar.setActionTextColor(ContextCompat.getColor(requireContext(), R.color.dark_accent))
        
        // Show the snackbar
        snackbar.show()
        
        // Log the permission denial
        android.util.Log.w("PracticesSummaryFragment", "Location permission denied, using seasonal weather data")
    }
    
    private fun showDefaultWeather(isPermissionDenied: Boolean = false) {
        // Show typical Indian weather based on current month
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val timeString = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date())
        
        when (month) {
            in 2..5 -> { // Summer (March-June)
                binding.weatherIcon.setImageResource(R.drawable.ic_sun)
                binding.weatherText.text = "🌡️ 35°C  💨 40%  ☀️ 0%"
                binding.weatherAdvice.text = "Estimated summer data (Time: $timeString)"
            }
            in 6..9 -> { // Monsoon (July-October)
                binding.weatherIcon.setImageResource(R.drawable.ic_rainy)
                binding.weatherText.text = "🌤️ 28°C  💧 75%  ☔ 60%"
                binding.weatherAdvice.text = "Estimated monsoon data (Time: $timeString)"
            }
            else -> { // Winter (November-February)
                binding.weatherIcon.setImageResource(R.drawable.ic_cloudy)
                binding.weatherText.text = "🌤️ 22°C  💨 55%  ☀️ 10%"
                binding.weatherAdvice.text = "Estimated winter data (Time: $timeString)"
            }
        }
    }
    
    private fun updateWeatherUI(weather: com.example.chinna.data.remote.WeatherData) {
        // Update weather icon based on condition and time of day
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val isDaytime = hourOfDay in 6..18
        
        val iconRes = when (weather.condition) {
            "Sunny" -> if (isDaytime) R.drawable.ic_sun else R.drawable.ic_cloudy // Night can't be sunny
            "Partly Cloudy" -> R.drawable.ic_cloudy
            "Cloudy" -> R.drawable.ic_cloudy
            "Rainy" -> R.drawable.ic_rainy
            "Stormy" -> R.drawable.ic_storm
            else -> if (isDaytime) R.drawable.ic_sun else R.drawable.ic_cloudy
        }
        binding.weatherIcon.setImageResource(iconRes)
        
        // Update weather text with simple icons
        val tempIcon = when {
            weather.temperature > 30 -> "🌡️"
            weather.temperature < 20 -> "❄️"
            else -> "🌤️"
        }
        
        val humidityIcon = when {
            weather.humidity > 70 -> "💧"
            weather.humidity < 30 -> "🏜️"
            else -> "💨"
        }
        
        val rainIcon = when {
            weather.rainChance > 50 -> "☔"
            weather.rainChance > 20 -> "🌦️"
            else -> "☀️"
        }
        
        // Make humidity more prominent
        binding.weatherText.text = "$tempIcon ${weather.temperature}°C  $humidityIcon ${weather.humidity}%  $rainIcon ${weather.rainChance}%"
        
        // Update weather advice based on conditions with focus on humidity
        val advice = when {
            weather.rainChance > 50 -> "Current conditions: High rain chance"
            weather.humidity > 80 -> "Current conditions: High humidity"
            weather.humidity > 70 -> "Current conditions: Elevated humidity"
            weather.humidity < 40 -> "Current conditions: Low humidity"
            weather.temperature > 35 -> "Current conditions: High temperature"
            else -> "${if (isDaytime) "Day" else "Night"} conditions: Humidity ${weather.humidity}%"
        }
        
        // Make sure time is shown prominently
        val timeString = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date())
        binding.weatherAdvice.text = "$advice (Time: $timeString)"
        
        // Log the weather data to help with debugging
        Log.d("WeatherData", "PIN code weather: ${weather.condition}, ${weather.temperature}°C, " +
              "Humidity: ${weather.humidity}%, Rain: ${weather.rainChance}%, Time: $timeString")
    }
    
    private fun updateQuickStats() {
        lifecycleScope.launch {
            try {
                // Set loading state
                requireActivity().runOnUiThread {
                    binding.daysToHarvestText.text = "Loading..."
                    binding.daysToFloweringText.text = "Loading..."
                    binding.avgYieldText.text = "Loading..."
                    binding.idealSoilText.text = "Loading..."
                    binding.idealHumidityText.text = "Loading..."
                    binding.idealTempText.text = "Loading..."
                    binding.conditionsSuitabilityText.text = "Checking conditions..."
                }
                
                // Get user data for location
                val userData = userRepository.getCurrentUserSync()
                val pinCode = userData?.pinCode ?: ""
                val soilType = userData?.soilType ?: "unknown"
                
                // Get weather data for suitability assessment
                val weatherData = weatherService.getCurrentWeather(pinCode)
                
                // Get current month for seasonal assessment
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-based month
                
                // Get days since sowing
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sowingDate = dateFormat.parse(args.sowingDate) ?: Date()
                val today = Date()
                val daysSinceSowing = TimeUnit.DAYS.convert(
                    today.time - sowingDate.time,
                    TimeUnit.MILLISECONDS
                ).toInt()
                
                // Get crop data from Gemini API
                val cropData = geminiService.getCropData(args.crop.name)
                
                // Extract crop data from API response or use defaults if parsing failed
                val harvestDays = cropData?.get("harvestDays")?.asInt ?: 110
                val floweringDays = cropData?.get("floweringDays")?.asInt ?: 35
                val avgYield = cropData?.get("yield")?.asString ?: "15-20 qtl/acre"
                val idealSoil = cropData?.get("soilType")?.asString ?: "Well-drained loamy"
                val idealTemp = cropData?.get("tempRange")?.asString ?: "25-32°C"
                val idealHumidityRainfall = cropData?.get("humidityRainfall")?.asString ?: "60-70%, moderate rainfall"
                
                // Calculate days remaining to harvest and flowering
                val daysToHarvest = (harvestDays - daysSinceSowing).coerceAtLeast(0)
                val daysToFlowering = (floweringDays - daysSinceSowing).coerceAtLeast(0)
                
                // Get suitability assessment from Gemini API
                val suitabilityResult = if (weatherData != null) {
                    geminiService.getCropSuitabilityAdvice(
                        crop = args.crop.name,
                        weatherData = weatherData,
                        soilType = soilType,
                        month = currentMonth
                    )
                } else {
                    // Fallback if weather data is unavailable
                    GeminiService.SuitabilityResult(false, "Weather data unavailable")
                }
                
                // Update UI with all the API-provided information
                requireActivity().runOnUiThread {
                    // Update crop stats
                    binding.daysToHarvestText.text = "$daysToHarvest days"
                    binding.daysToFloweringText.text = "$daysToFlowering days"
                    binding.avgYieldText.text = avgYield
                    binding.idealSoilText.text = idealSoil
                    binding.idealHumidityText.text = idealHumidityRainfall
                    binding.idealTempText.text = idealTemp
                    
                    // Update suitability assessment
                    if (suitabilityResult.isSuitable) {
                        binding.conditionsSuitabilityText.text = "Suitable for sowing (${suitabilityResult.reason})"
                        binding.conditionsSuitabilityText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_medium))
                    } else {
                        binding.conditionsSuitabilityText.text = "Not ideal (${suitabilityResult.reason})"
                        binding.conditionsSuitabilityText.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("PracticesSummaryFragment", "Error updating quick stats: ${e.message}")
                
                // Show error state in UI
                requireActivity().runOnUiThread {
                    binding.daysToHarvestText.text = "-- days"
                    binding.daysToFloweringText.text = "-- days"
                    binding.avgYieldText.text = "Not available"
                    binding.idealSoilText.text = "Not available"
                    binding.idealHumidityText.text = "Not available"
                    binding.idealTempText.text = "Not available"
                    binding.conditionsSuitabilityText.text = "Could not assess conditions"
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * For testing purposes only - tests the preemptive advice functionality with sample data
     */
    private fun testPreemptiveAdviceWithSampleData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Create sample weather data
            val sampleWeather = WeatherData(
                temperature = 30,
                humidity = 75,
                rainChance = 35,
                condition = "Partly Cloudy",
                iconCode = "partly_cloudy"
            )
            
            // Get growth stage
            val growthStage = getCurrentGrowthStage(calculateDaysSinceSowing(args.sowingDate))
            
            // Test with sample soil type
            val sampleSoilType = "black soil"
            
            // Call preemptive pest advice
            val preemptiveAdvice = geminiService.getPreemptivePestAdvice(
                crop = args.crop.name,
                growthStage = growthStage,
                weatherData = sampleWeather,
                soilType = sampleSoilType
            )
            
            // Log results
            println("TEST: Preemptive advice for ${args.crop.name} in $growthStage stage")
            println("TEST: Weather: ${sampleWeather.condition}, ${sampleWeather.temperature}°C, ${sampleWeather.humidity}% humidity")
            println("TEST: Soil type: $sampleSoilType")
            println("TEST: Advice received:")
            preemptiveAdvice.forEachIndexed { index, advice -> 
                println("TEST: ${index+1}. $advice") 
            }
            
            // Update UI with the test results
            if (preemptiveAdvice.isNotEmpty()) {
                // Old function updatePriorityTasks is removed
                requireActivity().runOnUiThread {
                    binding.preventionTask1.text = "• ${preemptiveAdvice.getOrNull(0) ?: ""}"
                    binding.preventionTask2.text = "• ${preemptiveAdvice.getOrNull(1) ?: ""}"
                    binding.preventionTask3.text = "• ${preemptiveAdvice.getOrNull(2) ?: ""}"
                    binding.priorityHeader.text = "🧪 Test Mode"
                }
            }
        }
    }
}