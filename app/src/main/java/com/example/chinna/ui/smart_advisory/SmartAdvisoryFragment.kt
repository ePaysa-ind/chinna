package com.example.chinna.ui.smart_advisory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.remote.GeminiService
import com.example.chinna.data.remote.WeatherService
import com.example.chinna.databinding.FragmentSmartAdvisoryBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import com.example.chinna.data.repository.UserRepository
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SmartAdvisoryFragment : Fragment() {
    
    private var _binding: FragmentSmartAdvisoryBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var weatherService: WeatherService
    
    @Inject
    lateinit var geminiService: GeminiService
    
    @Inject
    lateinit var prefsManager: PrefsManager
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private lateinit var nudgeAdapter: NudgeCardAdapter
    private val nudgeCards = mutableListOf<NudgeCard>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmartAdvisoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadSmartAdvisory()
    }
    
    private fun setupUI() {
        nudgeAdapter = NudgeCardAdapter(
            nudgeCards = nudgeCards,
            onCardClick = { nudgeCard ->
                toggleCardExpansion(nudgeCard)
            },
            onAskAdvice = { nudgeCard ->
                askGeminiAdvice(nudgeCard)
            }
        )
        
        binding.nudgeCardsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = nudgeAdapter
        }
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadSmartAdvisory()
        }
    }
    
    private fun loadSmartAdvisory() {
        lifecycleScope.launch {
            try {
                binding.swipeRefreshLayout.isRefreshing = true
                binding.loadingProgressBar.visibility = View.VISIBLE
                binding.contentScrollView.visibility = View.GONE
                binding.errorLayout.visibility = View.GONE
                
                // Get current user from database
                val currentUser = userRepository.getCurrentUserSync()
                
                if (currentUser == null) {
                    showError("User data not found. Please login again.")
                    return@launch
                }
                
                val selectedCrop = currentUser.crop
                val sowingDate = if (currentUser.sowingDate > 0) {
                    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(currentUser.sowingDate))
                } else {
                    null
                }
                
                if (selectedCrop.isEmpty()) {
                    showError("Please select a crop first")
                    return@launch
                }
                
                val growthStage = if (sowingDate == null) {
                    // If no sowing date, still show general advice
                    CropGrowthStage("planning", 0, 0, 0)
                } else {
                    // Calculate growth stage
                    calculateGrowthStage(sowingDate)
                }
                
                // Update UI labels based on sowing date
                if (sowingDate == null) {
                    binding.growthStageTextView.text = "Not Sown Yet"
                } else {
                    binding.growthStageTextView.text = growthStage.stageName
                }
                
                // Get weather data
                val weatherData = withContext(Dispatchers.IO) {
                    weatherService.getCurrentWeather()
                }
                
                // Update UI with crop and stage info
                binding.cropNameTextView.text = selectedCrop
                
                // Update weather info
                weatherData?.let {
                    binding.weatherCard.visibility = View.VISIBLE
                    binding.temperatureTextView.text = "${it.temperature}°C"
                    binding.humidityTextView.text = "${it.humidity}%"
                    binding.weatherConditionTextView.text = it.condition
                }
                
                // Generate nudge cards based on stage
                val cards = generateNudgeCards(selectedCrop, growthStage, weatherData)
                nudgeCards.clear()
                nudgeCards.addAll(cards)
                nudgeAdapter.notifyDataSetChanged()
                
                binding.contentScrollView.visibility = View.VISIBLE
                binding.loadingProgressBar.visibility = View.GONE
                
            } catch (e: Exception) {
                showError("Failed to load advisory: ${e.message}")
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun calculateGrowthStage(sowingDateStr: String): CropGrowthStage {
        try {
            // Parse the sowing date
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val sowingDate = dateFormat.parse(sowingDateStr) ?: Date()
            val currentDate = Date()
            
            // Calculate days since sowing
            val daysSinceSowing = TimeUnit.DAYS.convert(
                currentDate.time - sowingDate.time,
                TimeUnit.MILLISECONDS
            ).toInt().coerceAtLeast(0) // Ensure non-negative
            
            // Define crop-specific maturity cycle
            val totalDaysToMaturity = getCropMaturityDays(prefsManager.getSelectedCrop() ?: "")
            
            // Define stage boundaries as percentages of total growth
            val seedlingEnd = totalDaysToMaturity * 0.15
            val vegetativeEnd = totalDaysToMaturity * 0.45
            val floweringEnd = totalDaysToMaturity * 0.75
            val fruitingEnd = totalDaysToMaturity * 0.95
            
            return when {
                daysSinceSowing < seedlingEnd -> CropGrowthStage(
                    stageName = "Seedling Stage",
                    currentDay = daysSinceSowing,
                    totalDays = seedlingEnd.toInt(),
                    stageNumber = 1
                )
                daysSinceSowing < vegetativeEnd -> CropGrowthStage(
                    stageName = "Vegetative Stage",
                    currentDay = daysSinceSowing - seedlingEnd.toInt(),
                    totalDays = (vegetativeEnd - seedlingEnd).toInt(),
                    stageNumber = 2
                )
                daysSinceSowing < floweringEnd -> CropGrowthStage(
                    stageName = "Flowering Stage",
                    currentDay = daysSinceSowing - vegetativeEnd.toInt(),
                    totalDays = (floweringEnd - vegetativeEnd).toInt(),
                    stageNumber = 3
                )
                daysSinceSowing < fruitingEnd -> CropGrowthStage(
                    stageName = "Fruiting Stage",
                    currentDay = daysSinceSowing - floweringEnd.toInt(),
                    totalDays = (fruitingEnd - floweringEnd).toInt(),
                    stageNumber = 4
                )
                else -> CropGrowthStage(
                    stageName = "Maturity Stage",
                    currentDay = daysSinceSowing - fruitingEnd.toInt(),
                    totalDays = (totalDaysToMaturity - fruitingEnd).toInt(),
                    stageNumber = 5
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SmartAdvisory", "Error calculating growth stage", e)
            // Return a default stage if calculation fails
            return CropGrowthStage("Maturity Stage", 1, 20, 5)
        }
    }
    
    private fun getCropMaturityDays(cropName: String): Int {
        return when (cropName.lowercase()) {
            "tomatoes", "tomato" -> 90
            "chillies", "chilli" -> 80
            "okra" -> 60
            "cotton" -> 160
            "maize", "corn" -> 95
            "rice" -> 120
            "wheat" -> 110
            "soybean" -> 100
            else -> 100 // Default maturity cycle
        }
    }
    
    private fun generateNudgeCards(
        crop: String,
        stage: CropGrowthStage,
        weather: com.example.chinna.data.remote.WeatherData?
    ): List<NudgeCard> {
        val cards = mutableListOf<NudgeCard>()
        
        when (stage.stageNumber) {
            1 -> { // Seedling Stage
                cards.add(NudgeCard(
                    id = "1",
                    title = "🌱 Check Soil Moisture",
                    shortDescription = "Keep soil consistently moist for seedlings",
                    fullDescription = "Seedlings require consistent moisture. Check soil daily and water gently to avoid disturbing young roots.",
                    priority = if (weather?.humidity ?: 50 < 40) "high" else "medium",
                    isExpanded = false,
                    actionType = "watering"
                ))
                
                cards.add(NudgeCard(
                    id = "2",
                    title = "🛡️ Protect from Pests",
                    shortDescription = "Young plants are vulnerable to pests",
                    fullDescription = "Inspect seedlings daily for aphids, cutworms, or other pests. Use organic neem oil spray if needed.",
                    priority = "medium",
                    isExpanded = false,
                    actionType = "pest_control"
                ))
                
                cards.add(NudgeCard(
                    id = "3",
                    title = "☀️ Ensure Adequate Light",
                    shortDescription = "Seedlings need 6-8 hours of sunlight",
                    fullDescription = "Make sure your seedlings get enough light. If natural light is insufficient, consider grow lights.",
                    priority = "low",
                    isExpanded = false,
                    actionType = "light_management"
                ))
            }
            
            2 -> { // Vegetative Stage
                cards.add(NudgeCard(
                    id = "4",
                    title = "🥗 Apply Nitrogen Fertilizer",
                    shortDescription = "Boost leafy growth with nitrogen",
                    fullDescription = "Apply balanced NPK fertilizer with emphasis on nitrogen for healthy leaf development.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "fertilization"
                ))
                
                cards.add(NudgeCard(
                    id = "5",
                    title = "🌿 Prune for Better Growth",
                    shortDescription = "Remove damaged leaves and suckers",
                    fullDescription = "Prune dead or damaged leaves and remove suckers to direct energy to main stems.",
                    priority = "medium",
                    isExpanded = false,
                    actionType = "pruning"
                ))
                
                cards.add(NudgeCard(
                    id = "6",
                    title = "💧 Adjust Watering Schedule",
                    shortDescription = "Increase water as plants grow larger",
                    fullDescription = "Larger plants need more water. Deep water 2-3 times per week depending on weather.",
                    priority = if (weather?.temperature ?: 25 > 30) "high" else "medium",
                    isExpanded = false,
                    actionType = "watering"
                ))
            }
            
            3 -> { // Flowering Stage
                cards.add(NudgeCard(
                    id = "7",
                    title = "🌸 Support Flowering",
                    shortDescription = "Switch to phosphorus-rich fertilizer",
                    fullDescription = "Use fertilizer high in phosphorus to promote flower development and fruit setting.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "fertilization"
                ))
                
                cards.add(NudgeCard(
                    id = "8",
                    title = "🐝 Encourage Pollination",
                    shortDescription = "Help bees and pollinators",
                    fullDescription = "Plant companion flowers or hand-pollinate if needed. Avoid pesticides during flowering.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "pollination"
                ))
                
                cards.add(NudgeCard(
                    id = "9",
                    title = "🎯 Monitor for Diseases",
                    shortDescription = "Flowers are susceptible to fungal issues",
                    fullDescription = "Watch for powdery mildew or blight. Ensure good air circulation and avoid overhead watering.",
                    priority = if (weather?.humidity ?: 50 > 70) "high" else "medium",
                    isExpanded = false,
                    actionType = "disease_control"
                ))
            }
            
            4 -> { // Fruiting Stage
                cards.add(NudgeCard(
                    id = "10",
                    title = "🍅 Support Heavy Fruits",
                    shortDescription = "Add stakes or cages for support",
                    fullDescription = "Heavy fruits can break branches. Install support structures to prevent damage.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "plant_support"
                ))
                
                cards.add(NudgeCard(
                    id = "11",
                    title = "💎 Apply Potassium",
                    shortDescription = "Improve fruit quality with potassium",
                    fullDescription = "Potassium helps fruit development and improves taste. Apply potassium-rich fertilizer.",
                    priority = "medium",
                    isExpanded = false,
                    actionType = "fertilization"
                ))
                
                cards.add(NudgeCard(
                    id = "12",
                    title = "🌊 Consistent Watering",
                    shortDescription = "Prevent fruit cracking",
                    fullDescription = "Maintain consistent soil moisture to prevent fruit cracking or blossom end rot.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "watering"
                ))
            }
            
            5 -> { // Maturity Stage
                cards.add(NudgeCard(
                    id = "13",
                    title = "🎯 Check Ripeness",
                    shortDescription = "Monitor fruits for harvest readiness",
                    fullDescription = "Check color, firmness, and ease of separation from plant to determine ripeness.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "harvesting"
                ))
                
                cards.add(NudgeCard(
                    id = "14",
                    title = "📦 Prepare for Harvest",
                    shortDescription = "Get containers and tools ready",
                    fullDescription = "Clean harvesting tools and prepare storage containers. Plan harvest for cool morning hours.",
                    priority = "medium",
                    isExpanded = false,
                    actionType = "harvesting"
                ))
                
                cards.add(NudgeCard(
                    id = "15",
                    title = "🌾 Reduce Watering",
                    shortDescription = "Help fruits ripen properly",
                    fullDescription = "Gradually reduce watering to concentrate flavors and encourage ripening.",
                    priority = "medium",
                    isExpanded = false,
                    actionType = "watering"
                ))
            }
        }
        
        // Add weather-specific cards
        weather?.let {
            if (it.temperature > 35) {
                cards.add(0, NudgeCard(
                    id = "heat_warning",
                    title = "🔥 Heat Stress Alert",
                    shortDescription = "High temperature detected (${it.temperature}°C)",
                    fullDescription = "Provide shade during peak hours, increase watering frequency, and mulch to retain moisture.",
                    priority = "urgent",
                    isExpanded = false,
                    actionType = "weather_alert"
                ))
            }
            
            if (it.rainChance > 70) {
                cards.add(0, NudgeCard(
                    id = "rain_alert",
                    title = "🌧️ Rain Expected",
                    shortDescription = "High chance of rain (${it.rainChance}%)",
                    fullDescription = "Delay watering and fertilizing. Check drainage to prevent waterlogging.",
                    priority = "high",
                    isExpanded = false,
                    actionType = "weather_alert"
                ))
            }
        }
        
        return cards.take(3) // Return top 3 cards
    }
    
    private fun toggleCardExpansion(nudgeCard: NudgeCard) {
        val position = nudgeCards.indexOf(nudgeCard)
        if (position != -1) {
            nudgeCards[position].isExpanded = !nudgeCards[position].isExpanded
            nudgeAdapter.notifyItemChanged(position)
        }
    }
    
    private fun askGeminiAdvice(nudgeCard: NudgeCard) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                binding.aiResponseCard.visibility = View.VISIBLE
                binding.aiResponseProgressBar.visibility = View.VISIBLE
                binding.aiResponseTextView.visibility = View.GONE
                
                // Get user data to personalize the response
                val currentUser = userRepository.getCurrentUserSync()
                val userName = currentUser?.name ?: "cvr" // Default to a generic name
                val crop = currentUser?.crop ?: prefsManager.getSelectedCrop() ?: ""
                val pinCode = currentUser?.pinCode ?: "" // Get PIN code for weather lookup
                val stage = binding.growthStageTextView.text.toString()
                val weather = weatherService.getCurrentWeather(pinCode)
                
                val prompt = buildString {
                    append("I'm growing $crop in the $stage. ")
                    append("The specific concern is: ${nudgeCard.title} - ${nudgeCard.shortDescription}. ")
                    weather?.let {
                        append("Current weather: ${it.temperature}°C, ${it.humidity}% humidity, ${it.condition}. ")
                    }
                    append("Give extremely brief, direct advice. ")
                    append("DO NOT include a greeting. ")
                    append("Just tell me what to do in 2-3 short sentences.")
                }
                
                val response = withContext(Dispatchers.IO) {
                    geminiService.getAdvice(prompt, crop, mapOf(
                        "stage" to stage,
                        "concern" to nudgeCard.title,
                        "weather" to (weather?.condition ?: "normal")
                    ))
                }
                
                // Set the response text without greeting
                binding.aiResponseTextView.text = response
                binding.aiResponseTextView.visibility = View.VISIBLE
                binding.aiResponseProgressBar.visibility = View.GONE
                
            } catch (e: Exception) {
                binding.aiResponseTextView.text = "Try again later."
                binding.aiResponseTextView.visibility = View.VISIBLE
                binding.aiResponseProgressBar.visibility = View.GONE
            }
        }
    }
    
    private fun showError(message: String) {
        binding.contentScrollView.visibility = View.GONE
        binding.loadingProgressBar.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.errorTextView.text = message
        
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CropGrowthStage(
    val stageName: String,
    val currentDay: Int,
    val totalDays: Int,
    val stageNumber: Int
)

data class NudgeCard(
    val id: String,
    val title: String,
    val shortDescription: String,
    val fullDescription: String,
    val priority: String, // urgent, high, medium, low
    var isExpanded: Boolean = false,
    val actionType: String // watering, fertilization, pest_control, etc.
)