package com.example.chinna

import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class WeatherApiTest {
    
    @Test
    fun testGoogleWeatherApiByCoordinates() = runBlocking {
        val okHttpClient = OkHttpClient()
        
        // Use environment variable or a test config file for the API key instead of hardcoding
        // DO NOT use production keys in tests
        val apiKey = System.getenv("GOOGLE_WEATHER_API_KEY") ?: return@runBlocking // Skip test if API key not available
        
        // Hyderabad coordinates for testing
        val latitude = 17.385044
        val longitude = 78.486671
        
        // Google Weather API endpoint with URL parameters
        val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude"
        
        val request = Request.Builder()
            .url(url)
            .header("X-Goog-Api-Key", apiKey)
            .build()
    }
    
    /**
     * Test for weather lookup using PIN code
     * NOTE: This test will be dynamically configured at runtime using the user's actual PIN code
     * when the app is deployed
     */
    @Test
    fun testGoogleWeatherApiByPinCode() = runBlocking {
        val okHttpClient = OkHttpClient()
        
        // Use environment variable or a test config file for the API key instead of hardcoding
        // DO NOT use production keys in tests
        val apiKey = System.getenv("GOOGLE_WEATHER_API_KEY") ?: return@runBlocking // Skip test if API key not available
        
        // Test PIN code - in actual app, this will be dynamically set from user's profile
        // IMPORTANT: Pin codes will be obtained at runtime from the user's saved profile
        val samplePinCode = System.getenv("TEST_PIN_CODE") ?: return@runBlocking // Skip test if test PIN not available
        
        // Google Weather API endpoint with URL parameters
        val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.address=India+$samplePinCode"
        
        val request = Request.Builder()
            .url(url)
            .header("X-Goog-Api-Key", apiKey)
            .build()
        
        try {
            val response = okHttpClient.newCall(request).execute()
            println("Response Code: ${response.code}")
            println("Response Message: ${response.message}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                println("Response Body: $responseBody")
                
                responseBody?.let {
                    val json = JSONObject(it)
                    println("Parsed JSON: $json")
                    
                    // Try to parse weather data
                    val temperature = json.optDouble("temperature", -999.0)
                    val humidity = json.optDouble("humidity", -999.0)
                    val condition = json.optString("condition", "unknown")
                    
                    println("Temperature: $temperature")
                    println("Humidity: $humidity")
                    println("Condition: $condition")
                }
            } else {
                println("Error body: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}