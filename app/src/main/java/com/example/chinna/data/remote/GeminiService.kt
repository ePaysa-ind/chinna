package com.example.chinna.data.remote

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
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
            val prompt = """
                As an agricultural expert, provide ONLY proactive pest and disease prevention advice for ${crop.uppercase()} crops in the ${growthStage.uppercase()} stage.
                
                Current environment:
                - Temperature: ${weatherData?.temperature ?: "unknown"}°C
                - Humidity: ${weatherData?.humidity ?: "unknown"}%
                - Weather condition: ${weatherData?.condition ?: "unknown"}
                - Rain probability: ${weatherData?.rainChance ?: "unknown"}%
                - Soil type: ${soilType ?: "unknown"}
                
                CRITICAL INSTRUCTIONS:
                1. Provide ONLY preemptive advice to PREVENT pest/disease attacks that are LIKELY GIVEN THE CURRENT CONDITIONS
                2. Focus on 2-week period ahead, not daily tasks
                3. Name specific pests/diseases that are risks in these exact conditions
                4. Give EXACT dilution rates for preventive treatments (e.g., "5ml/L neem oil")
                5. Provide 3 SPECIFIC prevention tasks with exact quantities and timing
                6. ULTRA-STRICT RULE: Each task MUST BE < 7 words total, absolute maximum
                7. Include at least one organic/natural method
                8. Include at least one cultural practice (e.g., "Remove yellow leaves")
                9. Use only generic chemical names, never branded products
                10. Format as ultra-concise imperative statements with specific dilutions
                
                Example good output (EXACTLY follow this brevity):
                1. Spray neem oil (5ml/L) weekly.
                2. Remove yellow leaves.
                3. Apply wood ash near stems.
                
                FOCUS ONLY ON PREVENTION OF SPECIFIC PESTS LIKELY IN THESE EXACT CONDITIONS.
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
            // Return fallback advice if AI fails
            return listOf(
                "Spray neem oil (10ml/L)",
                "Check leaf undersides",
                "Apply wood ash near stems"
            )
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