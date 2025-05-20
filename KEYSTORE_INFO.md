# Keystore and Secure Credentials

## Keystore Information
- **Location**: `/mnt/c/Users/raman/AndroidStudioProjects/chinna/keystore/chinna_release.keystore`
- **Alias**: chinna
- **Password**: Stored in keystore.properties (not in git)

## API Keys Management
All API keys are now stored securely in `local.properties` which is excluded from git:

```properties
# API Keys
GEMINI_API_KEY=your_gemini_api_key
GOOGLE_WEATHER_API_KEY=your_weather_api_key
GOOGLE_MAPS_API_KEY=your_maps_api_key
FIREBASE_API_KEY=your_firebase_api_key
```

## Security Practices
1. The `google-services.json` file is now excluded from git
2. All sensitive credentials are stored in `local.properties`
3. API keys are accessed through BuildConfig variables
4. SHA-256 certificate fingerprints are stored in GitHub secrets

## Setup Instructions
1. Copy `local.properties.template` to `local.properties`
2. Replace placeholder values with actual API keys
3. For Firebase credentials, extract API key from google-services.json

## Generating SHA-256 Certificate Fingerprint
To view the certificate fingerprint:
```bash
keytool -list -v -keystore keystore/chinna_release.keystore -alias chinna
```

The SHA-256 fingerprint is used for Firebase authentication and API restrictions.