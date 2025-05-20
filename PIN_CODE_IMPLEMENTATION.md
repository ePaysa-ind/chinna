# PIN Code Implementation

This document summarizes the changes made to implement PIN code-based location lookup and enhance the login flow.

## Changes Overview

1. **PIN Code-Based Location**
   - Replaced village names with Indian PIN codes (6-digit postal codes)
   - Added PIN code validation using regex pattern `^[1-9][0-9]{5}$`
   - Updated WeatherService to use PIN codes for more accurate weather data

2. **Enhanced Login Flow**
   - Created a two-step login process:
     1. Mobile number verification
     2. User details form (pre-filled for returning users)
   - Implemented AuthActivityUpdated to replace the original AuthActivity

3. **Security Improvements**
   - Removed hardcoded API keys from source code
   - Added secure storage for API keys in local.properties
   - Added SHA-256 fingerprint management for Firebase

## Files Modified

### Layout Files
- `/app/src/main/res/layout/fragment_login.xml` - Updated to use PIN code field instead of village
- Created new layouts:
  - `/app/src/main/res/layout/layout_mobile_entry.xml` - For first login step
  - `/app/src/main/res/layout/layout_user_details.xml` - For second login step (with PIN code field)

### Data Layer
- `/app/src/main/java/com/example/chinna/data/local/database/UserEntity.kt` - Modified to use pinCode field
- `/app/src/main/java/com/example/chinna/ui/auth/UserData.kt` - Updated data class to store pinCode
- `/app/src/main/java/com/example/chinna/data/repository/UserRepository.kt` - Updated to handle pinCode parameter

### UI Layer
- `/app/src/main/java/com/example/chinna/ui/auth/AuthActivityUpdated.kt` - New implementation of auth flow
- `/app/src/main/java/com/example/chinna/ui/auth/AuthViewModel.kt` - Updated to support PIN code validation

### Weather API
- `/app/src/main/java/com/example/chinna/data/remote/WeatherService.kt` - Enhanced to support PIN code-based location lookup

### Testing
- `/app/src/test/java/com/example/chinna/WeatherApiTest.kt` - Updated to use environment variables instead of hardcoded values

### Configuration
- `/app/src/main/AndroidManifest.xml` - Updated to use AuthActivityUpdated as the main entry point
- `.gitignore` - Updated to exclude keystore.properties and other sensitive files

## PIN Code Validation

```kotlin
// Regex for validating Indian PIN codes (6 digits, first digit non-zero)
private val PIN_CODE_PATTERN = "^[1-9][0-9]{5}$".toRegex()

fun isValidPinCode(pinCode: String): Boolean {
    return pinCode.matches(PIN_CODE_PATTERN)
}
```

## Weather API Integration

The WeatherService now supports PIN code-based location lookup:

```kotlin
suspend fun getCurrentWeather(pinCode: String? = null): WeatherData? = withContext(Dispatchers.IO) {
    // If PIN code is provided, use it for weather lookup
    if (pinCode != null && pinCode.isNotEmpty()) {
        val weatherData = fetchWeatherDataByPinCode(pinCode)
        if (weatherData != null) {
            // Cache the result with PIN code
            cachedWeatherData = weatherData
            lastFetchTime = System.currentTimeMillis()
            lastPinCode = pinCode
            return@withContext weatherData
        }
    }
    // Fallback to device location...
}

private fun fetchWeatherDataByPinCode(pinCode: String): WeatherData? {
    // Use PIN code for location lookup in Google Weather API
    val url = "https://weather.googleapis.com/v1/currentConditions:lookup?key=$apiKey&location.address=India+$pinCode"
    // ...
}
```

## Login Flow

The new login flow implements a two-step process:

1. First step: Mobile number entry and verification
2. Second step: 
   - For new users: Complete user profile form with PIN code
   - For returning users: Pre-filled form with existing data (including PIN code)

## Migration Notes

- The original AuthActivity is kept in the manifest but with exported=false for backward compatibility
- The new AuthActivityUpdated is set as the launcher activity
- Tests have been updated to use environment variables instead of hardcoded values

## Security Recommendations

1. Always use the local.properties file for storing API keys
2. Keep the keystore file secure and backed up
3. Register both SHA-1 and SHA-256 fingerprints with Firebase
4. Use Firebase App Check in production to prevent API abuse