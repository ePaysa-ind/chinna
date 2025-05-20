#!/bin/bash

# Script to generate a signed APK for Chinna App

echo "Generating signed APK for Chinna App..."
echo "----------------------------------------"

# Navigate to project directory
cd /mnt/c/Users/raman/AndroidStudioProjects/chinna

# Clean the project
echo "Cleaning project..."
./gradlew clean

# Build release APK
echo "Building signed release APK..."
./gradlew assembleRelease

# Check if build was successful
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    echo "----------------------------------------"
    echo "✅ Build Successful!"
    echo "Signed APK is available at:"
    echo "app/build/outputs/apk/release/app-release.apk"
    
    # Copy to a more accessible location
    mkdir -p release
    cp app/build/outputs/apk/release/app-release.apk release/chinna-app-1.0.0.apk
    echo ""
    echo "A copy has been placed at:"
    echo "release/chinna-app-1.0.0.apk"
else
    echo "----------------------------------------"
    echo "❌ Build Failed!"
    echo "Check build logs for errors."
fi