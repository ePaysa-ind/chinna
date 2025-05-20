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
    fun testGoogleWeatherApi() = runBlocking {
        val okHttpClient = OkHttpClient()
        val apiKey = "AIzaSyDOdWhhkPl4Lc5FN9A3NU1or0CARD_YkR8"
        
        // Hyderabad coordinates for testing
        val latitude = 17.385044
        val longitude = 78.486671
        
        // Google Weather API endpoint with URL parameters
        val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude"
        
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