package com.example.chinna.ui.practices

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        // Don't show local name
        binding.cropLocalName.text = ""
        
        // Check location permission and load weather
        checkLocationPermission()
        
        // Calculate days since sowing
        val daysSinceSowing = calculateDaysSinceSowing(args.sowingDate)
        val currentStage = getCurrentGrowthStage(daysSinceSowing)
        val progress = calculateProgress(daysSinceSowing)
        
        // Set growth stage
        binding.growthStageText.text = currentStage
        binding.growthProgressBar.progress = progress
        binding.daysText.text = "Day $daysSinceSowing"
        
        // Set progress color based on stage
        val progressColor = when (currentStage) {
            "Seedling" -> R.color.green_light
            "Vegetative" -> R.color.green_medium
            "Flowering" -> R.color.amber
            "Fruiting" -> R.color.orange
            "Harvest Ready" -> R.color.dark_secondary
            else -> R.color.dark_text_secondary
        }
        binding.growthProgressBar.progressTintList = 
            ContextCompat.getColorStateList(requireContext(), progressColor)
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
        
        // Load AI-powered weather recommendations
        loadWeatherRecommendations(currentWeekTasks)
        
        // Set critical alerts
        if (criticalReminders.isNotEmpty()) {
            binding.alertCard.visibility = View.VISIBLE
            binding.alertText.text = criticalReminders.joinToString("\n")
        } else {
            binding.alertCard.visibility = View.GONE
        }
        
        // Update quick stats
        updateQuickStats()
        
        // Weather will be loaded separately with permissions
        binding.weatherIcon.setImageResource(R.drawable.ic_sun)
        binding.weatherText.text = "Loading weather..."
    }
    
    private fun loadWeatherRecommendations(scheduledTasks: List<Pair<String, String>>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get current weather data
                val weather = weatherService.getCurrentWeather()
                val growthStage = getCurrentGrowthStage(calculateDaysSinceSowing(args.sowingDate))
                
                // Get soil type from user preferences
                val user = prefsManager.getUser()
                val soilType = user?.soilType ?: "unknown"
                
                // Get preemptive pest advice for the next two weeks
                val preemptiveAdvice = geminiService.getPreemptivePestAdvice(
                    crop = args.crop.name,
                    growthStage = growthStage,
                    weatherData = weather,
                    soilType = soilType
                )
                
                // Update UI with preemptive advice
                if (preemptiveAdvice.isNotEmpty()) {
                    updatePriorityTasks(preemptiveAdvice)
                    
                    // Show header for biweekly tasks
                    requireActivity().runOnUiThread {
                        binding.priorityHeader.text = "Two-Week Prevention Tasks"
                    }
                } else {
                    // Fallback to traditional recommendation if preemptive advice fails
                    getTraditionalRecommendations(scheduledTasks, weather, growthStage)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to scheduled tasks if AI fails
                updatePriorityTasksFromSchedule(scheduledTasks)
            }
        }
    }
    
    private suspend fun getTraditionalRecommendations(
        scheduledTasks: List<Pair<String, String>>, 
        weather: WeatherData?, 
        growthStage: String
    ) {
        // Prepare prompt for Gemini
        val prompt = buildString {
            appendLine("As an agricultural expert, provide weather-based recommendations for ${args.crop.name} cultivation.")
            appendLine("Current details:")
            appendLine("- Growth stage: $growthStage")
            weather?.let { weatherData ->
                appendLine("- Weather: ${weatherData.condition}, Temperature: ${weatherData.temperature}°C")
                appendLine("- Humidity: ${weatherData.humidity}%, Rain chance: ${weatherData.rainChance}%")
            }
            appendLine("- Scheduled activities: ${scheduledTasks.joinToString(", ") { task -> task.first }}")
            appendLine()
            appendLine("Provide exactly 3 priority recommendations for today in simple English:")
            appendLine("1. One recommendation about weeding based on weather")
            appendLine("2. One recommendation about pesticide/disease management based on humidity and temperature")
            appendLine("3. One recommendation about irrigation/spraying based on rain chance")
            appendLine()
            appendLine("Format each recommendation as a simple action statement without numbering.")
            appendLine("ULTRA-STRICT RULE: Keep each recommendation under 7 words total.")
            appendLine("For example: \"Spray neem oil (5ml/L)\", \"Remove yellow leaves\", \"Apply wood ash\".")
        }
        
        // Get AI recommendations
        val recommendations = getGeminiRecommendations(prompt)
        
        // Update UI with recommendations
        updatePriorityTasks(recommendations)
    }
    
    private suspend fun getGeminiRecommendations(prompt: String): List<String> {
        return try {
            val model = com.google.ai.client.generativeai.GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = com.example.chinna.BuildConfig.GEMINI_API_KEY
            )
            
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            
            // Parse the recommendations
            text.lines()
                .filter { it.isNotBlank() }
                .take(3)
                .map { it.trim() }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list on error
            emptyList()
        }
    }
    
    private fun updatePriorityTasks(recommendations: List<String>) {
        requireActivity().runOnUiThread {
            if (recommendations.isEmpty()) {
                // Default fallback recommendations
                binding.priorityTask1.text = "• Check weather forecast"
                binding.priorityTask2.text = "• Monitor crop health"
                binding.priorityTask3.text = "• Follow cultivation practices"
            } else {
                // Format recommendations with bullet points and ensure they contain specific instructions
                // Include dilution rates if they're missing
                val task1 = recommendations.getOrNull(0) ?: "Check weather conditions"
                val task2 = recommendations.getOrNull(1) ?: "Monitor pest activity"
                val task3 = recommendations.getOrNull(2) ?: "Manage irrigation"
                
                // Ensure task format is appropriate with dilution rates if applicable
                binding.priorityTask1.text = "• $task1"
                binding.priorityTask2.text = "• $task2"
                binding.priorityTask3.text = "• $task3"
                
                // Show all tasks
                binding.priorityTask1.visibility = View.VISIBLE
                binding.priorityTask2.visibility = View.VISIBLE
                binding.priorityTask3.visibility = View.VISIBLE
            }
        }
    }
    
    private fun updatePriorityTasksFromSchedule(scheduledTasks: List<Pair<String, String>>) {
        requireActivity().runOnUiThread {
            // Update header to indicate these are scheduled tasks, not AI-generated prevention tasks
            binding.priorityHeader.text = "🎯 Scheduled Tasks (Next Two Weeks)" 
            
            if (scheduledTasks.isEmpty()) {
                binding.priorityTask1.text = "• No scheduled tasks for this period"
                binding.priorityTask2.visibility = View.GONE
                binding.priorityTask3.visibility = View.GONE
            } else {
                // Group tasks by type to show a more cohesive biweekly plan
                val tasks = scheduledTasks.map { it.first }.distinct()
                
                binding.priorityTask1.text = "• ${tasks.getOrNull(0) ?: ""}"
                binding.priorityTask2.text = "• ${tasks.getOrNull(1) ?: ""}"
                binding.priorityTask3.text = "• ${tasks.getOrNull(2) ?: ""}"
                
                // Hide unused task views
                if (tasks.size < 3) binding.priorityTask3.visibility = View.GONE
                if (tasks.size < 2) binding.priorityTask2.visibility = View.GONE
                
                // Show all available tasks
                binding.priorityTask1.visibility = View.VISIBLE
                if (tasks.size >= 2) binding.priorityTask2.visibility = View.VISIBLE
                if (tasks.size >= 3) binding.priorityTask3.visibility = View.VISIBLE
            }
        }
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
            in 15..35 -> "Vegetative"
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
    
    private fun calculateHealthScore(days: Int, expectedWeek: Int): Int {
        // Calculate health score based on how well the farmer is following the schedule
        val actualWeek = (days / 7) + 1
        
        // Retrieve past task completion from shared preferences
        val prefs = requireContext().getSharedPreferences("practices_completion", android.content.Context.MODE_PRIVATE)
        val cropId = args.crop.id
        
        // Count completed tasks for past weeks
        var completedTasks = 0
        var totalTasks = 0
        
        // Check completion for each past week
        for (week in 1 until actualWeek) {
            val weekKey = "${cropId}_week_${week}_completed"
            val weekTotalKey = "${cropId}_week_${week}_total"
            
            completedTasks += prefs.getInt(weekKey, 0)
            totalTasks += prefs.getInt(weekTotalKey, 3) // Default 3 tasks per week
        }
        
        // Calculate base score from task completion
        val completionRate = if (totalTasks > 0) {
            (completedTasks.toFloat() / totalTasks.toFloat()) * 100
        } else {
            100f // No past tasks yet, assume good health
        }
        
        // Apply penalties for being off schedule
        val weekDifference = kotlin.math.abs(expectedWeek - actualWeek)
        val schedulePenalty = when (weekDifference) {
            0 -> 0
            1 -> 10
            2 -> 25
            else -> 40
        }
        
        // Final health score
        val healthScore = (completionRate - schedulePenalty).coerceIn(0f, 100f)
        return healthScore.toInt()
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
        
        when (month) {
            in 2..5 -> { // Summer (March-June)
                binding.weatherIcon.setImageResource(R.drawable.ic_sun)
                binding.weatherText.text = "🌡️ 35°C  💨 40%  ☀️ 0%"
                binding.weatherAdvice.text = if (isPermissionDenied) {
                    "Using estimated seasonal data (summer)"
                } else {
                    "Good for spraying"
                }
            }
            in 6..9 -> { // Monsoon (July-October)
                binding.weatherIcon.setImageResource(R.drawable.ic_rainy)
                binding.weatherText.text = "🌤️ 28°C  💧 75%  ☔ 60%"
                binding.weatherAdvice.text = if (isPermissionDenied) {
                    "Using estimated seasonal data (monsoon)"
                } else {
                    "Skip spraying today"
                }
            }
            else -> { // Winter (November-February)
                binding.weatherIcon.setImageResource(R.drawable.ic_cloudy)
                binding.weatherText.text = "🌤️ 22°C  💨 55%  ☀️ 10%"
                binding.weatherAdvice.text = if (isPermissionDenied) {
                    "Using estimated seasonal data (winter)"
                } else {
                    "Monitor conditions"
                }
            }
        }
    }
    
    private fun updateWeatherUI(weather: com.example.chinna.data.remote.WeatherData) {
        // Update weather icon based on condition
        val iconRes = when (weather.condition) {
            "Sunny" -> R.drawable.ic_sun
            "Partly Cloudy" -> R.drawable.ic_cloudy
            "Cloudy" -> R.drawable.ic_cloudy
            "Rainy" -> R.drawable.ic_rainy
            "Stormy" -> R.drawable.ic_storm
            else -> R.drawable.ic_sun
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
        
        binding.weatherText.text = "$tempIcon ${weather.temperature}°C  $humidityIcon ${weather.humidity}%  $rainIcon ${weather.rainChance}%"
        
        // Update weather advice based on conditions
        val advice = when {
            weather.rainChance > 50 -> "Skip spraying today"
            weather.humidity > 70 -> "Watch for disease"
            weather.temperature > 35 -> "Irrigate in evening"
            weather.condition == "Sunny" -> "Good for spraying"
            else -> "Monitor conditions"
        }
        binding.weatherAdvice.text = advice
    }
    
    private fun updateQuickStats() {
        // Load crop data to get harvest days
        try {
            val json = requireContext().assets.open("crops_data.json")
                .bufferedReader()
                .use { it.readText() }
                
            val jsonObject = Gson().fromJson(json, JsonObject::class.java)
            val cropsArray = jsonObject.getAsJsonArray("crops")
            
            var harvestDays = 50 // Default
            cropsArray.forEach { element ->
                val cropObj = element.asJsonObject
                if (cropObj.get("id").asString == args.crop.id) {
                    harvestDays = cropObj.get("harvestDays")?.asInt ?: 50
                    return@forEach
                }
            }
            
            // Calculate days to harvest
            val sowingDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(args.sowingDate)
            val today = Date()
            val daysSinceSowing = TimeUnit.DAYS.convert(
                today.time - sowingDate.time,
                TimeUnit.MILLISECONDS
            ).toInt()
            
            val daysToHarvest = (harvestDays - daysSinceSowing).coerceAtLeast(0)
            
            // Update UI
            binding.daysToHarvestText.text = "$daysToHarvest days"
            
            // Simple health score based on schedule adherence
            val expectedWeek = ((daysSinceSowing.toFloat() / harvestDays.toFloat()) * 12).toInt() + 1
            val actualWeek = (daysSinceSowing / 7) + 1
            val healthScore = when {
                actualWeek == expectedWeek -> 95
                kotlin.math.abs(actualWeek - expectedWeek) == 1 -> 85
                else -> 70
            }
            
            val healthIcon = when {
                healthScore >= 90 -> "💚"
                healthScore >= 70 -> "💛"
                else -> "❤️"
            }
            
            binding.healthScoreText.text = "$healthScore%"
            
        } catch (e: Exception) {
            e.printStackTrace()
            binding.daysToHarvestText.text = "-- days"
            binding.healthScoreText.text = "--%"
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
                updatePriorityTasks(preemptiveAdvice)
                requireActivity().runOnUiThread {
                    binding.priorityHeader.text = "🧪 Test: Two-Week Prevention Tasks"
                }
            }
        }
    }
}
