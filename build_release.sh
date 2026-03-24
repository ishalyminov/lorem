#!/bin/bash

# Location Reminder - Release APK Build Script

set -e

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_SDK="/mnt/c/Users/ishalyminov/AppData/Local/Android/Sdk"
KEYSTORE_FILE="$PROJECT_ROOT/keystore/location_reminder.jks"
KEY_STORE_PASSWORD="android"
KEY_ALIAS="location_reminder"
KEY_PASSWORD="android"

echo "=== Location Reminder Release Build ==="
echo "Project Root: $PROJECT_ROOT"
echo "Android SDK: $ANDROID_SDK"
echo "Keystore: $KEYSTORE_FILE"
echo ""

# Verify keystore exists
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "ERROR: Keystore file not found at $KEYSTORE_FILE"
    exit 1
fi

echo "Verifying keystore..."
keytool -printcert -file "$KEYSTORE_FILE" -storepass "$KEY_STORE_PASSWORD" > /dev/null 2>&1 && \
    echo "Keystore verified successfully!" || {
        echo "WARNING: Keystore verification failed. Continuing anyway..."
}

# Set environment variables for Android SDK
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"

# Check if gradlew exists
if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
    echo "ERROR: gradlew not found in project root"
    exit 1
fi

echo ""
echo "=== Starting Gradle Build ==="
cd "$PROJECT_ROOT"

# Clean and build release APK
./gradlew clean assembleRelease --stacktrace

echo ""
echo "=== Build Complete ==="
echo ""

# Find the APK file
APK_FILE=$(find "$PROJECT_ROOT/app/build/outputs/apk/release" -name "*.apk" 2>/dev/null | tail -1)

if [ -n "$APK_FILE" ] && [ -f "$APK_FILE" ]; then
    echo "APK located at: $APK_FILE"
    
    # Get APK details
    echo ""
    echo "=== APK Details ==="
    UNZIP_SIZE=$(unzip -l "$APK_FILE" 2>/dev/null | awk '/name.*\.apk$/ {print $4}' | cut -d'/' -f1)
    if [ -z "$UNZIP_SIZE" ]; then
        UNZIP_SIZE="N/A"
    fi
    echo "Package Name: com.example.locationreminder"
    
    # Print SHA256, MD5, and SHA signatures from keystore
    echo ""
    echo "=== Keystore Information ==="
    keytool -printcert -file "$KEYSTORE_FILE" -storepass "$KEY_STORE_PASSWORD" 2>/dev/null || \
        echo "Keystore info not available (keystore type or permissions issue)"
else
    echo "WARNING: APK file not found. Check build output above."
    ls -la "$PROJECT_ROOT/app/build/outputs/apk/release/" 2>/dev/null || true
fi

echo ""
echo "=== Build Summary ==="
echo "To install on device:"
echo "  adb install '$APK_FILE' (for release build)"
echo "  adb install-multiple --non-raw '/path/to/other-files.zip'"
echo ""