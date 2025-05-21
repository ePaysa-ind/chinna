# Chinna - AI Crop Assistant

AI-powered pest and disease identification app for Indian farmers, optimized for older Android devices with dark theme for reduced eye strain in bright sunlight.

## Features

- 📱 **Phone Authentication**: OTP-based login using Firebase with improved flow for returning users
- 📸 **AI Pest Identification**: Gemini 1.5 Flash with confidence % display
- 🌿 **Plant Validation**: Validates and identifies plant type
- 🖼️ **Gallery Support**: Pick from gallery or use camera
- 🌐 **Offline Support**: Works without internet connection
- 🌾 **Package of Practices**: API-driven personalized crop management guides for 8 crops
- 🗓️ **2-Week Scheduling**: API-based actionable farm activity timeline
- 🔍 **Crop Statistics**: Dynamic statistics from AI (harvest days, flowering, yield, etc.)
- 🐛 **Pest Prevention**: Region-specific pest prevention advice via API
- 📊 **History Tracking**: View past pest identification results
- 🌙 **Dark Theme**: Designed for outdoor use in bright sunlight
- 📋 **Form Validation**: Data validation with clear feedback
- 📍 **PIN Code Location**: Precise weather forecasts using 6-digit Indian PIN codes
- 🔄 **Smart Pre-fill**: Auto-fills user details for returning users

## Supported Crops

- Okra
- Tomato
- Chilli
- Rice
- Wheat
- Cotton
- Maize
- Soybean

## Technologies

- Kotlin
- MVVM Architecture
- Firebase Authentication
- Google Gemini AI (1.5 Flash)
- Google Weather API
- CameraX
- Navigation Component
- Hilt Dependency Injection
- Material Design 3
- API-driven content architecture

## Setup

1. Clone the repository
2. Create a `local.properties` file with:
   ```properties
   sdk.dir=YOUR_ANDROID_SDK_PATH
   GEMINI_API_KEY=YOUR_GEMINI_API_KEY
   GOOGLE_WEATHER_API_KEY=YOUR_WEATHER_API_KEY
   GOOGLE_MAPS_API_KEY=YOUR_MAPS_API_KEY
   FIREBASE_API_KEY=YOUR_FIREBASE_API_KEY
   FIREBASE_PROJECT_ID=YOUR_FIREBASE_PROJECT_ID
   FIREBASE_APP_ID=YOUR_FIREBASE_APP_ID
   ```
3. Add Firebase configuration (`google-services.json`) to the app directory
4. Generate SHA-256 fingerprint for Firebase:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Add the SHA-256 fingerprint to your Firebase project
6. Build and run the project

## Target Audience

Designed specifically for Indian farmers with:
- Older Android devices (Android 7.0+)
- Limited connectivity in rural areas
- Need for clear, easy-to-read UI in sunlight
- Grade 3 English text for accessibility

## UI/UX Features

- Dark background (#121212) for reduced eye strain
- Large fonts (22sp default, 32sp headers)
- High contrast text
- Gold accent color
- Emoji indicators for better recognition
- Simplified user flows
- Touch-friendly large targets (64dp+)

## Build

```bash
./gradlew assembleDebug
```

## License

[Proprietary] - All rights reserved