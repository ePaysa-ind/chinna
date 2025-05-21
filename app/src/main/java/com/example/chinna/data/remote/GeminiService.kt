package com.example.chinna.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.Serializable
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiService @Inject constructor() {
    
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = com.example.chinna.BuildConfig.GEMINI_API_KEY
        )
    }
    
    suspend fun analyzeCropImage(bitmap: Bitmap): AnalysisResult {
        try {
            val prompt = """
                You are an agricultural expert. Your task is to analyze images strictly for agricultural purposes.
                
                Step 1: Check if this is a valid agricultural image
                - Is this a real plant, crop, or leaf? (not a drawing, not a non-plant object)
                - Does it appear to be artificially marked or spoofed? (pen marks, sketches)
                
                If NOT a valid plant/crop/leaf OR if it appears spoofed:
                - PEST/DISEASE NAME: Not a valid plant
                - CONFIDENCE: 100%
                - PLANT NAME: Unknown
                - POSSIBLE PLANTS: Not applicable
                - SUMMARY: Please upload a real photo of a plant, leaf or crop
                - TREATMENT: Not applicable
                - PREVENTION: Not applicable
                
                If it IS a valid plant image, analyze for pests or diseases:
                1. PEST/DISEASE NAME: [Name of the pest or disease]
                2. CONFIDENCE: [0-100%] (your confidence level in this identification)
                3. PLANT NAME: [Identified plant common English name only, no scientific names, otherwise "Unknown"]
                4. POSSIBLE PLANTS: [If uncertain, list 2-3 possible plants using common English names only, separated by commas]
                5. SEVERITY: [HIGH/MEDIUM/LOW]
                6. SUMMARY: [One sentence description - if plant identified, start with "Your [plant name] has..."; if not certain, start with "This plant (possibly [plant1] or [plant2]) has..."]
                7. TREATMENT: [Simple treatment in 1-2 sentences using common medicines]
                8. PREVENTION: [Prevention methods separated by periods. Each method as a separate sentence]
                
                Use simple Grade 3 English. If no pest or disease is found, say "Healthy crop" with high confidence.
                IMPORTANT: Use plain text only. Do not use markdown formatting, asterisks, or any special characters.
                For prevention, provide 2-3 methods separated by periods.
            """.trimIndent()
            
            val response = model.generateContent(
                com.google.ai.client.generativeai.type.content {
                    image(bitmap)
                    text(prompt)
                }
            )
            
            return parseResponse(response.text ?: "")
        } catch (e: Exception) {
            // Provide more specific error messages
            when {
                e.message?.contains("API key") == true -> throw Exception("API key issue. Please check your Gemini API key.")
                e.message?.contains("Network") == true -> throw Exception("Network error. Please check your internet connection.")
                e.message?.contains("timeout") == true -> throw Exception("Request timed out. Please try again.")
                else -> throw Exception("Analysis failed: ${e.message}")
            }
        }
    }
    
    private fun parseResponse(response: String): AnalysisResult {
        val lines = response.lines()
        var pestName = "Unknown"
        var confidence = "0%"
        var plantName = "Unknown"
        var possiblePlants = ""
        var severity = "LOW"
        var summary = "Unable to analyze"
        var treatment = "Consult local expert"
        var prevention = "Regular monitoring"
        
        lines.forEach { line ->
            when {
                line.contains("PEST/DISEASE NAME:", ignoreCase = true) -> {
                    pestName = line.substringAfter(":").trim()
                }
                line.contains("CONFIDENCE:", ignoreCase = true) -> {
                    confidence = line.substringAfter(":").trim()
                }
                line.contains("PLANT NAME:", ignoreCase = true) -> {
                    plantName = line.substringAfter(":").trim()
                }
                line.contains("POSSIBLE PLANTS:", ignoreCase = true) -> {
                    possiblePlants = line.substringAfter(":").trim()
                }
                line.contains("SEVERITY:", ignoreCase = true) -> {
                    severity = line.substringAfter(":").trim().uppercase()
                }
                line.contains("SUMMARY:", ignoreCase = true) -> {
                    summary = line.substringAfter(":").trim()
                }
                line.contains("TREATMENT:", ignoreCase = true) -> {
                    treatment = line.substringAfter(":").trim()
                }
                line.contains("PREVENTION:", ignoreCase = true) -> {
                    prevention = line.substringAfter(":").trim()
                }
            }
        }
        
        return AnalysisResult(
            pestName = pestName,
            confidence = confidence,
            plantName = plantName,
            possiblePlants = possiblePlants,
            severity = severity,
            summary = summary,
            treatment = treatment,
            prevention = prevention
        )
    }
    
    data class AnalysisResult(
        val pestName: String,
        val confidence: String,
        val plantName: String,
        val possiblePlants: String,
        val severity: String,
        val summary: String,
        val treatment: String,
        val prevention: String
    )
    
    /**
     * Data class for crop suitability assessment results
     * Implements Serializable to prevent cast issues
     */
    data class SuitabilityResult(
        val isSuitable: Boolean,
        val reason: String
    ) : Serializable
    
    suspend fun getAdvice(prompt: String, crop: String, context: Map<String, String>): String {
        try {
            val fullPrompt = """
                You are an agricultural expert helping Indian farmers with extremely concise advice. 
                Provide advice in very simple Grade 3 English.
                
                Context:
                - Crop: $crop
                - Stage: ${context["stage"] ?: "unknown"}
                - Concern: ${context["concern"] ?: "general"}
                - Weather: ${context["weather"] ?: "normal"}
                - Temperature: ${context["temperature"] ?: "unknown"}
                - Humidity: ${context["humidity"] ?: "unknown"}
                - Rain chance: ${context["rainChance"] ?: "unknown"}
                - Soil type: ${context["soilType"] ?: "unknown"}
                
                Query: $prompt
                
                CRITICAL RULES:
                1. BREVITY IS ESSENTIAL - use only 2-3 short sentences total
                2. Use only generic chemical names, never brand names
                3. Suggest cost-effective solutions like "use cow dung" 
                4. Remove ALL descriptions, explanations, greetings, and pleasantries
                5. Start directly with the practical action - "Add nitrogen"
                6. Use ONLY simple words a rural farmer would understand
                7. Maximum response length is 50 words
                8. Include practical quantities (e.g., "1 handful per plant")
                9. Focus on organic methods (cow dung, neem, etc.) first
                10. Mention 1-2 specific actions at most
                11. Format should be: Action 1. Action 2. (No numbering, just sentences)
                12. ALWAYS include specific dilution rates for pesticides (e.g., "mix 1 part neem oil with 10 parts water")
                
                Example good response: "Mix 1 cup urea with 10L water. Apply weekly to roots. Cow dung also good."
                Example bad response: "To enhance growth, consider applying nitrogen-rich fertilizer. You can also use organic matter for nutrients."
                
                REMEMBER: EXTREME BREVITY IS REQUIRED. NO PLEASANTRIES OR EXPLANATIONS.
            """.trimIndent()
            
            val response = model.generateContent(fullPrompt)
            
            // Basic profanity filtering and brevity enforcement
            val processedResponse = processBriefResponse(response.text ?: "")
            
            return if (processedResponse.isBlank()) {
                "Use cow dung as natural fertilizer. Watch for pests."
            } else {
                processedResponse
            }
        } catch (e: Exception) {
            return when {
                e.message?.contains("API key") == true -> "API error. Try again later."
                e.message?.contains("Network") == true -> "Check internet connection."
                else -> "Try again later."
            }
        }
    }
    
    /**
     * Get preemptive pest management advice based on crop, weather conditions, and soil type
     */
    suspend fun getPreemptivePestAdvice(
        crop: String, 
        growthStage: String,
        weatherData: WeatherData?,
        soilType: String?
    ): List<String> {
        try {
            // Get current time to provide to Gemini
            val timeString = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date())
            val isDaytime = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 6..18
            val timeOfDay = if (isDaytime) "day" else "night"
            
            // Log the inputs being sent to Gemini to help with debugging
            android.util.Log.d("GeminiService", "Requesting pest advice for $crop at $growthStage stage")
            android.util.Log.d("GeminiService", "Weather data: temp=${weatherData?.temperature}°C, " +
                "humidity=${weatherData?.humidity}%, condition=${weatherData?.condition}, time=$timeString ($timeOfDay)")
            
            val prompt = """
                As an agricultural expert, provide ONLY proactive pest and disease prevention advice for ${crop.uppercase()} crops in the ${growthStage.uppercase()} stage.
                
                Current environment:
                - Temperature: ${weatherData?.temperature ?: "unknown"}°C
                - Humidity: ${weatherData?.humidity ?: "unknown"}% (VERY IMPORTANT FACTOR)
                - Weather condition: ${weatherData?.condition ?: "unknown"}
                - Rain probability: ${weatherData?.rainChance ?: "unknown"}%
                - Soil type: ${soilType ?: "unknown"}
                - Current time: $timeString ($timeOfDay time)
                
                CRITICAL INSTRUCTIONS:
                1. HUMIDITY IS THE MOST IMPORTANT FACTOR - base your first recommendation on humidity level
                2. Provide ONLY preemptive advice to PREVENT pest/disease attacks that are LIKELY GIVEN THE CURRENT CONDITIONS
                3. Focus on 2-week period ahead, not daily tasks
                4. Name specific pests/diseases that are risks in these exact conditions
                5. Give EXACT dilution rates for preventive treatments (e.g., "5ml/L neem oil")
                6. Provide 3 SPECIFIC prevention tasks with exact quantities and timing
                7. ULTRA-STRICT RULE: Each task MUST BE < 8 words total, absolute maximum
                8. Include at least one organic/natural method (neem oil, cow dung, etc.)
                9. Include at least one cultural practice (e.g., "Remove yellow leaves")
                10. Use only generic chemical names, never branded products
                11. Format as ultra-concise imperative statements with specific dilutions
                
                HUMIDITY-SPECIFIC GUIDANCE:
                - HIGH HUMIDITY (>70%): Focus on fungal disease prevention
                - LOW HUMIDITY (<40%): Focus on pest prevention
                - MEDIUM HUMIDITY: Balance of both
                
                Example good output (EXACTLY follow this brevity):
                1. Spray neem oil (5ml/L) weekly.
                2. Remove yellowing leaves immediately.
                3. Apply wood ash around stems.
                
                FOCUS ONLY ON PREVENTION OF SPECIFIC PESTS LIKELY IN THESE EXACT HUMIDITY CONDITIONS.
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            
            // Extract the numbered advice points
            val adviceList = text.lines()
                .filter { line -> 
                    // Look for lines that start with numbers or bullet points
                    line.trim().matches(Regex("^[0-9•\\-*]+.*")) && line.length > 10 
                }
                .map { line ->
                    // Clean up the advice - remove numbers and excess spaces
                    line.trim().replace(Regex("^[0-9•\\-*]+\\.?\\s*"), "").trim()
                }
                .filter { it.isNotEmpty() }
                .take(3) // Ensure we only have 3 items
            
            // If we don't have 3 items, try an alternative parsing approach
            if (adviceList.size < 3) {
                // Split by periods and take sentences
                return text.split(".")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 10 }
                    .take(3)
            }
            
            return adviceList
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list for the UI layer to handle
            return emptyList()
        }
    }
    
    private fun processBriefResponse(text: String): String {
        // Filter inappropriate content
        var processed = filterInappropriateContent(text)
        
        // Remove common pleasantries and introductions
        val phrasesToRemove = listOf(
            "hi", "hello", "greetings", "dear farmer", "happy farming", "hope this helps",
            "i hope", "i recommend", "i suggest", "i advise", "you should", "you can", 
            "you might", "you may", "consider", "remember that", "note that", "keep in mind",
            "it's important", "it is important", "it is recommended", "it's recommended",
            "for your", "for better", "for optimal", "for the best", "this will", "this helps"
        )
        
        phrasesToRemove.forEach { phrase ->
            // Remove phrase from start of text with case insensitivity
            val patternStart = "^\\s*${Regex.escape(phrase)}\\s+".toRegex(RegexOption.IGNORE_CASE)
            processed = processed.replace(patternStart, "")
            
            // Remove phrase with exclamation or period after it
            val patternWithPunctuation = "${Regex.escape(phrase)}[!.]?\\s+".toRegex(RegexOption.IGNORE_CASE)
            processed = processed.replace(patternWithPunctuation, "")
        }
        
        // Capitalize first letter of each sentence and ensure proper ending punctuation
        processed = processed.split(". ")
            .filter { it.isNotBlank() }
            .joinToString(". ") { sentence ->
                val trimmed = sentence.trim()
                if (trimmed.isNotEmpty()) {
                    val firstChar = trimmed[0].uppercaseChar()
                    val rest = if (trimmed.length > 1) trimmed.substring(1) else ""
                    "$firstChar$rest"
                } else {
                    ""
                }
            }
        
        // Ensure text ends with a period if it doesn't already
        if (!processed.endsWith(".") && !processed.endsWith("!") && !processed.endsWith("?")) {
            processed += "."
        }
        
        // Enforce maximum length (approx. 50 words)
        val words = processed.split(Regex("\\s+"))
        if (words.size > 50) {
            processed = words.take(50).joinToString(" ") + "."
        }
        
        return processed
    }
    
    /**
     * Get suitability advice for a specific crop based on current weather and location
     * @return Serializable Pair of Boolean (is suitable) and String (reason)
     */
    suspend fun getCropSuitabilityAdvice(
        crop: String,
        weatherData: WeatherData,
        soilType: String,
        month: Int
    ): SuitabilityResult {
        try {
            // Convert month number to season for India
            val season = when(month) {
                in 6..10 -> "Kharif (Monsoon)" // June-October
                in 11..12, in 1..3 -> "Rabi (Winter)" // November-March
                else -> "Summer" // April-May
            }
            
            // Log the inputs being sent to Gemini to help with debugging
            android.util.Log.d("GeminiService", "Requesting crop suitability for $crop in $season")
            android.util.Log.d("GeminiService", "Weather data: temp=${weatherData.temperature}°C, " +
                "humidity=${weatherData.humidity}%, condition=${weatherData.condition}")
            
            val prompt = """
                As an agricultural expert, analyze whether the current conditions are suitable for growing ${crop.uppercase()} in India.
                
                Current environment:
                - Temperature: ${weatherData.temperature}°C
                - Humidity: ${weatherData.humidity}%
                - Weather condition: ${weatherData.condition}
                - Rain probability: ${weatherData.rainChance}%
                - Soil type: $soilType
                - Current season: $season (Month: $month)
                
                CRITICAL INSTRUCTIONS:
                1. Compare current conditions with ideal growing conditions for $crop in India
                2. Consider BOTH the crop's needs AND the current season in India (Kharif/Rabi/Summer)
                3. Assess if this is the right SEASON for planting this crop (e.g. Kharif crops in monsoon)
                4. Consider water availability based on rainfall chance
                5. First respond with either [SUITABLE] or [NOT SUITABLE] on a line by itself
                6. Then provide ONE BRIEF REASON why (max 8 words) - the exact reason will be shown to farmers
                
                RESPONSE FORMAT (EXACTLY 2 LINES):
                [SUITABLE] or [NOT SUITABLE]
                Brief reason (max 8 words)
                
                Example good output:
                [SUITABLE]
                Ideal Kharif season with sufficient rainfall
                
                OR
                
                [NOT SUITABLE]
                Not Kharif season, temperature too high
                
                STRICT RULE: Response must start with [SUITABLE] or [NOT SUITABLE] status marker on first line, followed by brief reason on second line.
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            
            // Extract the suitability marker and reason
            val lines = text.lines().filter { it.isNotBlank() }
            
            if (lines.size >= 2) {
                val isSuitable = lines[0].contains("[SUITABLE]", ignoreCase = true) && 
                                !lines[0].contains("NOT", ignoreCase = true)
                
                // Clean up the reason - remove any tags or brackets
                val reason = lines[1].replace(Regex("\\[.*?\\]"), "").trim()
                
                return SuitabilityResult(isSuitable, reason)
            } else if (lines.isNotEmpty()) {
                // If we only have one line, try to parse it
                val isSuitable = lines[0].contains("[SUITABLE]", ignoreCase = true) && 
                                !lines[0].contains("NOT", ignoreCase = true)
                val reason = if (isSuitable) "Conditions match growing requirements" else "Conditions not optimal"
                
                return SuitabilityResult(isSuitable, reason)
            }
            
            // Default fallback if parsing fails
            return SuitabilityResult(false, "Could not assess conditions")
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Return fallback assessment if AI fails
            val isSuitable = false
            val reason = "Unable to check conditions"
            
            return SuitabilityResult(isSuitable, reason)
        }
    }
    
    /**
     * Get specific sowing practices for a crop based on location and conditions
     */
    suspend fun getSowingPractices(
        crop: String,
        pinCode: String,
        soilType: String,
        weatherData: WeatherData?
    ): List<String> {
        try {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-based month for API
            
            val season = when(month) {
                in 6..10 -> "Kharif (Monsoon)" // June-October
                in 11..12, in 1..3 -> "Rabi (Winter)" // November-March
                else -> "Summer" // April-May
            }
            
            val prompt = """
                As an agricultural expert, provide 3 specific sowing practices for ${crop.uppercase()} in India.
                
                Farmer location and conditions:
                - PIN code: $pinCode (region in India)
                - Current season: $season
                - Temperature: ${weatherData?.temperature ?: "unknown"}°C
                - Humidity: ${weatherData?.humidity ?: "unknown"}%
                - Weather: ${weatherData?.condition ?: "unknown"}
                - Soil type: $soilType
                
                CRITICAL INSTRUCTIONS:
                1. Provide EXACT row spacing measurements (in cm) for this crop in this region
                2. Include SPECIFIC pre-sowing soil preparation advice
                3. Give PRECISE irrigation timing after sowing
                4. Include specific seed treatment if applicable
                5. Each practice must be a single, concise action statement (max 10 words)
                6. Do NOT include generic advice like "follow recommended spacing"
                7. All advice must be specific to Indian farming conditions
                8. Use simple language a rural farmer can understand
                9. Focus on immediate sowing activities only
                
                For example, if the crop is rice, include exact spacing like "Plant in rows 20cm apart, 15cm between plants"
                
                FORMAT YOUR RESPONSE AS EXACTLY 3 NUMBERED POINTS:
                1. [First specific practice with exact measurements]
                2. [Second specific practice]
                3. [Third specific practice]
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            
            // Extract the numbered practices
            val practicesList = text.lines()
                .filter { line -> 
                    line.trim().matches(Regex("^[0-9]+\\..*")) && line.length > 5
                }
                .map { line ->
                    line.replaceFirst(Regex("^[0-9]+\\.\\s*"), "").trim()
                }
                .filter { it.isNotEmpty() }
                .take(3)
            
            // If we couldn't extract numbered practices, try to get sentences
            if (practicesList.isEmpty()) {
                return text
                    .replace("\n", " ")
                    .split(".")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 5 }
                    .take(3)
            }
            
            return practicesList
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list for the UI layer to handle
            return emptyList()
        }
    }
    
    /**
     * Get specific maintenance practices for a crop based on growth stage and conditions
     */
    suspend fun getMaintenancePractices(
        crop: String,
        growthStage: String,
        daysSinceSowing: Int,
        pinCode: String,
        soilType: String,
        weatherData: WeatherData?
    ): List<String> {
        try {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-based month for API
            
            val prompt = """
                As an agricultural expert, provide 3 specific maintenance practices for ${crop.uppercase()} crops in India.
                
                Crop details and conditions:
                - Current growth stage: $growthStage
                - Days since sowing: $daysSinceSowing days
                - PIN code: $pinCode (region in India)
                - Temperature: ${weatherData?.temperature ?: "unknown"}°C
                - Humidity: ${weatherData?.humidity ?: "unknown"}%
                - Weather: ${weatherData?.condition ?: "unknown"}
                - Soil type: $soilType
                - Current month: Month $month
                
                CRITICAL INSTRUCTIONS:
                1. Focus ONLY on the tasks needed in the NEXT TWO WEEKS
                2. Include PRECISE fertilizer applications with EXACT quantities (e.g., "Apply 50kg urea/acre")
                3. Include specific irrigation schedule appropriate for this growth stage
                4. Include any weeding, thinning, or other cultural practices needed now
                5. Each practice must be a single, concise action statement (max 10 words)
                6. Do NOT include generic advice like "monitor growth"
                7. Make recommendations specific to Indian farming practices
                8. Use simple language a rural farmer can understand
                9. If fertilizer is needed, specify exactly which one and how much
                
                FOR EXAMPLE (for rice in vegetative stage):
                1. Apply 50kg urea per acre now
                2. Maintain 5cm water level in field
                3. Remove weeds by hand or rotary weeder
                
                FORMAT YOUR RESPONSE AS EXACTLY 3 NUMBERED POINTS:
                1. [First specific practice with exact measurements]
                2. [Second specific practice]
                3. [Third specific practice]
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            
            // Extract the numbered practices
            val practicesList = text.lines()
                .filter { line -> 
                    line.trim().matches(Regex("^[0-9]+\\..*")) && line.length > 5
                }
                .map { line ->
                    line.replaceFirst(Regex("^[0-9]+\\.\\s*"), "").trim()
                }
                .filter { it.isNotEmpty() }
                .take(3)
            
            // If we couldn't extract numbered practices, try to get sentences
            if (practicesList.isEmpty()) {
                return text
                    .replace("\n", " ")
                    .split(".")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 5 }
                    .take(3)
            }
            
            return practicesList
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list for the UI layer to handle
            return emptyList()
        }
    }
    
    /**
     * Get crop data statistics for a specific crop
     */
    suspend fun getCropData(crop: String): JsonObject? {
        try {
            val cropDataPrompt = """As an agricultural expert, provide ONLY the following stats for ${crop.uppercase()} crops in India:
                1. Days to harvest from sowing
                2. Days to flowering from sowing
                3. Average yield (as range in qtl/acre)
                4. Ideal soil type
                5. Ideal temperature range (in °C)
                6. Ideal humidity and rainfall
                
                Format as JSON with keys: harvestDays (number), floweringDays (number), yield (string), 
                soilType (string), tempRange (string), humidityRainfall (string)
            """.trimIndent()
            
            val cropDataResponse = model.generateContent(cropDataPrompt)
            val cropDataText = cropDataResponse.text ?: ""
            
            // Try to parse JSON response
            val jsonPattern = "\\{[^}]*\\}".toRegex()
            val jsonMatch = jsonPattern.find(cropDataText)?.value
            
            return if (jsonMatch != null) {
                Gson().fromJson(jsonMatch, JsonObject::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Get likely pests and diseases for a crop in current conditions
     */
    suspend fun getLikelyPests(
        crop: String,
        growthStage: String,
        daysSinceSowing: Int,
        weatherData: WeatherData?,
        pinCode: String,
        soilType: String,
        month: Int
    ): String {
        try {
            // Get season for context
            val season = when(month) {
                in 6..10 -> "Kharif (Monsoon)" // June-October
                in 11..12, in 1..3 -> "Rabi (Winter)" // November-March
                else -> "Summer" // April-May
            }
            
            val prompt = """
                As an agricultural expert, ONLY identify the specific pests and diseases most likely to affect ${crop.uppercase()} crops in India right now.
                
                Crop and environmental details:
                - Growth stage: $growthStage
                - Days since sowing: $daysSinceSowing days
                - Current season: $season
                - Temperature: ${weatherData?.temperature ?: "unknown"}°C
                - Humidity: ${weatherData?.humidity ?: "unknown"}%
                - Weather condition: ${weatherData?.condition ?: "unknown"}
                - Rain probability: ${weatherData?.rainChance ?: "unknown"}%
                - Soil type: $soilType
                - Region: India (PIN: $pinCode)
                
                CRITICAL INSTRUCTIONS:
                1. ONLY list the 2-3 most likely pest/disease threats in the CURRENT CONDITIONS
                2. Name the EXACT pests/diseases (scientific and common names)
                3. Focus on SPECIFIC pests/diseases, not generic categories
                4. Return ONLY the list prefixed with "Likely risks:" - no other text
                5. Maximum response length is 10 words total
                6. Do not explain why they are likely - just list them
                7. Focus on the most immediate threats in the next 2 weeks
                
                Example good responses:
                "Likely risks: Rice blast, Brown planthopper"
                "Likely risks: Aphids, Powdery mildew, Cutworms"
                
                STRICT RULE: Response must start with "Likely risks:" followed by specific pest/disease names only.
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            
            // Extract just the likely risks line
            val likelyRisks = text.lines()
                .firstOrNull { line -> 
                    line.trim().startsWith("Likely risks:", ignoreCase = true) && line.length > 12
                }
                ?.trim() ?: ""
            
            // If we found a likely risks line, return it
            if (likelyRisks.isNotEmpty()) {
                return likelyRisks
            }
            
            // Otherwise try to construct a reasonable response from the text
            val possibleRisks = text.replace("\n", " ").trim()
            if (possibleRisks.length > 5) {
                return if (possibleRisks.contains("risk", ignoreCase = true) || 
                           possibleRisks.contains("pest", ignoreCase = true) || 
                           possibleRisks.contains("disease", ignoreCase = true)) {
                    possibleRisks
                } else {
                    "Likely risks: $possibleRisks"
                }
            }
            
            // If all else fails
            return "Likely risks: Monitor for common seasonal pests"
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty string for the UI layer to handle
            return ""
        }
    }
    
    private fun filterInappropriateContent(text: String): String {
        // Basic profanity filter - can be expanded with a more comprehensive list
        val inappropriateWords = listOf<String>(
            // Add inappropriate words to filter
            // This list should be comprehensive but I'm keeping it minimal for this implementation
        )
        
        // Filter out any inappropriate content
        var filtered = text
        inappropriateWords.forEach { word ->
            filtered = filtered.replace(word, "***", ignoreCase = true)
        }
        
        // Check if response is farming-related
        val farmingKeywords = listOf(
            "crop", "plant", "soil", "water", "pest", "disease", "fertilizer",
            "harvest", "seed", "weather", "farming", "agriculture", "grow"
        )
        
        val isFarmingRelated = farmingKeywords.any { keyword ->
            filtered.contains(keyword, ignoreCase = true)
        }
        
        return if (isFarmingRelated) filtered else ""
    }
}