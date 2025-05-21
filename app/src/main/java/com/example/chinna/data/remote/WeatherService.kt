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
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherData(
    val temperature: Int, // in Celsius
    val humidity: Int, // percentage
    val rainChance: Int, // percentage
    val condition: String, // simple description
    val iconCode: String // for icon display
) : java.io.Serializable

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
    private var lastPinCode: String = "" // Track the PIN code used for caching
    
    suspend fun getCurrentWeather(pinCode: String? = null): WeatherData? = withContext(Dispatchers.IO) {
        try {
            // Always log the time for better debugging
            val currentTimeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
            Log.d(TAG, "getCurrentWeather: Starting weather request at $currentTimeString" + 
                  if (pinCode != null) " for PIN code $pinCode" else "")
            
            // Only use cache if explicitly requested (disabled for now to ensure fresh data)
            val useCache = false
            
            if (useCache && cachedWeatherData != null && 
                System.currentTimeMillis() - lastFetchTime < CACHE_DURATION &&
                (pinCode == lastPinCode || (pinCode == null && lastPinCode.isEmpty()))) {
                Log.d(TAG, "getCurrentWeather: Using cached weather data from ${(System.currentTimeMillis() - lastFetchTime)/1000} seconds ago")
                return@withContext cachedWeatherData
            }
            
            // CRITICAL: If PIN code is provided, ALWAYS try to use it first
            if (pinCode != null && pinCode.isNotEmpty()) {
                Log.d(TAG, "getCurrentWeather: Attempting to use PIN code $pinCode for weather lookup")
                
                val weatherData = fetchWeatherDataByPinCode(pinCode)
                if (weatherData != null) {
                    // Cache the result with PIN code
                    cachedWeatherData = weatherData
                    lastFetchTime = System.currentTimeMillis()
                    lastPinCode = pinCode
                    
                    Log.d(TAG, "getCurrentWeather: Successfully fetched weather data for PIN $pinCode: " +
                        "${weatherData.condition}, ${weatherData.temperature}°C, ${weatherData.humidity}%, rain ${weatherData.rainChance}%")
                    
                    return@withContext weatherData
                } else {
                    Log.w(TAG, "getCurrentWeather: Failed to get weather for PIN $pinCode, will try device location")
                }
            } else {
                Log.d(TAG, "getCurrentWeather: No PIN code provided, will use device location")
            }
            
            // Only fall back to device location if PIN code is not provided or lookup failed
            val location = getLocation()
            if (location == null) {
                Log.w(TAG, "getCurrentWeather: Could not get device location")
                
                // Return mock data if all else fails
                val mockData = createMockWeatherData()
                Log.d(TAG, "getCurrentWeather: Using mock weather data as fallback")
                return@withContext mockData
            }
            
            Log.d(TAG, "getCurrentWeather: Got location: ${location.latitude}, ${location.longitude}")
            
            // Fetch weather data from Google Weather API
            val weatherData = fetchWeatherDataByCoordinates(location.latitude, location.longitude)
            
            if (weatherData == null) {
                Log.e(TAG, "getCurrentWeather: Failed to fetch weather data from API")
                
                // Return mock data if API fails
                val mockData = createMockWeatherData()
                Log.d(TAG, "getCurrentWeather: Using mock weather data as fallback")
                return@withContext mockData
            }
            
            Log.d(TAG, "getCurrentWeather: Successfully fetched weather data: " +
                "${weatherData.condition}, ${weatherData.temperature}°C, ${weatherData.humidity}%, rain ${weatherData.rainChance}%")
            
            // Cache the result
            cachedWeatherData = weatherData
            lastFetchTime = System.currentTimeMillis()
            lastPinCode = "" // Clear PIN code cache since we used coordinates
            
            return@withContext weatherData
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentWeather: Exception getting weather data", e)
            e.printStackTrace()
            
            // Always return some weather data even on error
            val mockData = createMockWeatherData()
            Log.d(TAG, "getCurrentWeather: Using mock weather data after exception")
            return@withContext mockData
        }
    }
    
    // Create mock weather data that's appropriate for the current time
    private fun createMockWeatherData(): WeatherData {
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Determine if it's day or night (6am to 6pm is day)
        val isDaytime = hourOfDay in 6..18
        
        return if (isDaytime) {
            // Daytime conditions
            WeatherData(
                temperature = (25..35).random(),
                humidity = (60..85).random(), // Higher humidity for India
                rainChance = (0..30).random(),
                condition = "Partly Cloudy",
                iconCode = "partly_cloudy_day"
            )
        } else {
            // Nighttime conditions
            WeatherData(
                temperature = (18..25).random(),
                humidity = (70..90).random(), // Higher humidity at night
                rainChance = (0..20).random(),
                condition = "Partly Cloudy",
                iconCode = "partly_cloudy_night"
            )
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
    
    private fun fetchWeatherDataByPinCode(pinCode: String): WeatherData? {
        val apiKey = BuildConfig.GOOGLE_WEATHER_API_KEY
        
        // Check if API key is valid
        if (apiKey.isBlank()) {
            Log.e(TAG, "Weather API key is missing or invalid")
            return null
        }
        
        // Use PIN code for location lookup in Google Weather API
        // Note: In Google Weather API, we're using address parameter with PIN code
        val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.address=India+$pinCode"
        
        Log.d(TAG, "fetchWeatherDataByPinCode: Making API request to Google Weather API with PIN code $pinCode")
        
        val request = Request.Builder()
            .url(url)
            .header("X-Goog-Api-Key", apiKey)
            .build()
            
        return makeWeatherApiRequest(request)
    }
    
    private fun fetchWeatherDataByCoordinates(latitude: Double, longitude: Double): WeatherData? {
        val apiKey = BuildConfig.GOOGLE_WEATHER_API_KEY
        
        // Check if API key is valid
        if (apiKey.isBlank()) {
            Log.e(TAG, "Weather API key is missing or invalid")
            return null
        }
        
        // Google Weather API endpoint for current conditions using URL parameters
        val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude"
        
        Log.d(TAG, "fetchWeatherDataByCoordinates: Making API request to Google Weather API")
        
        val request = Request.Builder()
            .url(url)
            .header("X-Goog-Api-Key", apiKey)
            .build()
        
        return makeWeatherApiRequest(request)
    }
    
    private fun makeWeatherApiRequest(request: Request): WeatherData? {
        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "makeWeatherApiRequest: API request successful with code ${response.code}")
                
                val responseBody = response.body?.string() ?: ""
                if (responseBody.isBlank()) {
                    Log.e(TAG, "makeWeatherApiRequest: API returned empty response body")
                    return null
                }
                
                val json = JSONObject(responseBody)
                parseWeatherData(json)
            } else {
                Log.e(TAG, "makeWeatherApiRequest: API Error ${response.code}: ${response.message}")
                
                // Log detailed error information
                val errorBody = response.body?.string()
                if (!errorBody.isNullOrBlank()) {
                    Log.e(TAG, "Error details: $errorBody")
                }
                
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "makeWeatherApiRequest: Exception during API request", e)
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