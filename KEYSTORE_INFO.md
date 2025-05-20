# Keystore and Secure Credentials

## New Release Keystore Information
- **Location**: `/mnt/c/Users/raman/AndroidStudioProjects/chinna/keystore/chinna_release_new.keystore`
- **Alias**: chinna
- **Password**: chinnaapp123
- **Type**: PKCS12
- **Validity**: 10,000 days (until Oct 5, 2052)

## Certificate Fingerprints for Google Play & Firebase
- **SHA-1**: `DA:D5:0D:26:09:B3:DD:70:3B:B0:1A:3D:08:21:CA:7B:FD:98:03:97`
- **SHA-256**: `1C:A7:9F:36:07:86:32:2B:82:29:1B:75:D4:8B:36:EB:61:CF:5B:6B:F7:3B:D3:A1:7B:74:84:DA:A9:EB:A2:34`

## API Keys Management
All API keys are stored securely in `local.properties` which is excluded from git:

```properties
# API Keys
GEMINI_API_KEY=your_gemini_api_key
GOOGLE_WEATHER_API_KEY=your_weather_api_key
GOOGLE_MAPS_API_KEY=your_maps_api_key
FIREBASE_API_KEY=your_firebase_api_key
```

## Security Practices
1. The `google-services.json` file is excluded from git
2. All sensitive credentials are stored in `local.properties`
3. API keys are accessed through BuildConfig variables
4. SHA-256 certificate fingerprints are added to Firebase console
5. The keystore uses PKCS12 format (modern, secure format)
6. The keystore has alphanumeric passwords without special characters

## Keystore Backup Instructions
1. Store a backup of the keystore file in a secure location
2. Document the keystore password in a password manager
3. Keep a record of the certificate fingerprints
4. DO NOT lose this keystore - it's required for all future app updates

## Generating Signed APK/App Bundle
To generate a signed APK or App Bundle for Google Play:
```bash
./gradlew assembleRelease
```
Or use Android Studio: Build → Generate Signed Bundle/APK

## Viewing Certificate Fingerprints
```bash
keytool -list -v -keystore keystore/chinna_release_new.keystore -storepass chinnaapp123
```