package com.example.chinna.data.remote

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.chinna.BuildConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherData(
    val temperature: Int, // in Celsius
    val humidity: Int, // percentage
    val rainChance: Int, // percentage
    val condition: String, // simple description
    val iconCode: String // for icon display
)

@Singleton
class WeatherService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "WeatherService"
        private const val CACHE_DURATION = 30 * 60 * 1000L // 30 minutes
    }
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    // Create OkHttpClient with logging for debug builds
    private val okHttpClient = OkHttpClient.Builder().apply {
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            addInterceptor(loggingInterceptor)
        }
    }.build()
    
    // Cache weather data for 30 minutes
    private var cachedWeatherData: WeatherData? = null
    private var lastFetchTime: Long = 0
    
    suspend fun getCurrentWeather(): WeatherData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getCurrentWeather: Starting weather request")
            
            // Check cache first
            if (cachedWeatherData != null && 
                System.currentTimeMillis() - lastFetchTime < CACHE_DURATION) {
                Log.d(TAG, "getCurrentWeather: Using cached weather data from ${(System.currentTimeMillis() - lastFetchTime)/1000} seconds ago")
                return@withContext cachedWeatherData
            }
            
            // Get current location
            val location = getLocation()
            if (location == null) {
                Log.w(TAG, "getCurrentWeather: Could not get device location")
                return@withContext null
            }
            
            Log.d(TAG, "getCurrentWeather: Got location: ${location.latitude}, ${location.longitude}")
            
            // Fetch weather data from Google Weather API
            val weatherData = fetchWeatherData(location.latitude, location.longitude)
            
            if (weatherData == null) {
                Log.e(TAG, "getCurrentWeather: Failed to fetch weather data from API")
                return@withContext null
            }
            
            Log.d(TAG, "getCurrentWeather: Successfully fetched weather data: " +
                "${weatherData.condition}, ${weatherData.temperature}°C, ${weatherData.humidity}%, rain ${weatherData.rainChance}%")
            
            // Cache the result
            cachedWeatherData = weatherData
            lastFetchTime = System.currentTimeMillis()
            
            return@withContext weatherData
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentWeather: Exception getting weather data", e)
            e.printStackTrace()
            return@withContext null
        }
    }
    
    private suspend fun getLocation(): Location? {
        return try {
            Log.d(TAG, "getLocation: Requesting device location")
            val location = fusedLocationClient.lastLocation.await()
            
            if (location == null) {
                Log.w(TAG, "getLocation: Location is null, device location not available")
            } else {
                Log.d(TAG, "getLocation: Successfully got location: ${location.latitude}, ${location.longitude}")
            }
            
            location
        } catch (e: Exception) {
            Log.e(TAG, "getLocation: Error getting location", e)
            null
        }
    }
    
    private fun fetchWeatherData(latitude: Double, longitude: Double): WeatherData? {
        val apiKey = BuildConfig.GOOGLE_WEATHER_API_KEY
        
        // Check if API key is valid
        if (apiKey.isBlank() || apiKey == "your_actual_google_weather_api_key_here") {
            Log.e(TAG, "Weather API key is missing or invalid")
            return null
        }
        
        // Google Weather API endpoint for current conditions using URL parameters
        val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude"
        
        Log.d(TAG, "fetchWeatherData: Making API request to Google Weather API")
        
        val request = Request.Builder()
            .url(url)
            .header("X-Goog-Api-Key", apiKey)
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "fetchWeatherData: API request successful with code ${response.code}")
                
                val responseBody = response.body?.string() ?: ""
                if (responseBody.isBlank()) {
                    Log.e(TAG, "fetchWeatherData: API returned empty response body")
                    return null
                }
                
                val json = JSONObject(responseBody)
                parseWeatherData(json)
            } else {
                Log.e(TAG, "fetchWeatherData: API Error ${response.code}: ${response.message}")
                
                // Log detailed error information
                val errorBody = response.body?.string()
                if (!errorBody.isNullOrBlank()) {
                    Log.e(TAG, "Error details: $errorBody")
                }
                
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchWeatherData: Exception during API request", e)
            null
        }
    }
    
    private fun parseWeatherData(json: JSONObject): WeatherData? {
        return try {
            Log.d(TAG, "parseWeatherData: Parsing weather data JSON")
            
            // Google Weather API returns current conditions in the response
            val temperature = json.getDouble("temperature").toInt()
            val humidity = (json.getDouble("humidity") * 100).toInt()
            
            // Get weather condition and icon
            val condition = json.getString("condition")
            val icon = json.getString("icon")
            
            // For rain chance, we'll need to make a forecast request or estimate from conditions
            val rainChance = estimateRainChance(condition)
            
            val simpleCondition = getSimpleCondition(condition)
            
            Log.d(TAG, "parseWeatherData: Successfully parsed weather data: " +
                "temp=$temperature°C, humidity=$humidity%, condition=$simpleCondition, " +
                "rainChance=$rainChance%, icon=$icon")
            
            WeatherData(
                temperature = temperature,
                humidity = humidity,
                rainChance = rainChance,
                condition = simpleCondition,
                iconCode = icon
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseWeatherData: Error parsing weather data", e)
            Log.e(TAG, "JSON content: $json")
            null
        }
    }
    
    private fun estimateRainChance(condition: String): Int {
        return when (condition.lowercase()) {
            "rain", "drizzle", "rain_light", "rain_heavy" -> 80
            "storm", "thunderstorm" -> 90
            "showers", "showers_light", "showers_heavy" -> 70
            "cloudy", "overcast", "mostly_cloudy" -> 30
            "partly_cloudy" -> 20
            "clear", "sunny", "mostly_sunny" -> 5
            else -> 15
        }
    }
    
    private fun getSimpleCondition(condition: String): String {
        return when (condition.lowercase()) {
            "clear", "sunny", "clear_day", "clear_night" -> "Sunny"
            "partly_cloudy", "partly_cloudy_day", "partly_cloudy_night" -> "Partly Cloudy"
            "cloudy", "overcast", "mostly_cloudy" -> "Cloudy"
            "rain", "rain_light", "rain_heavy", "drizzle" -> "Rainy"
            "storm", "thunderstorm" -> "Stormy"
            "fog", "mist", "haze" -> "Foggy"
            "snow", "snow_light", "snow_heavy" -> "Snowy"
            else -> "Normal"
        }
    }
}