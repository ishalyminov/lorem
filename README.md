# Location Reminder App

An Android app that allows users to save reminders tied to locations. When you enter within a specified proximity of a saved location, the app shows you a notification with the reminder text.

This is an MVP built without any paid APIs - all data is stored locally using SQLite/Room.

![Target SDK](https://img.shields.io/badge/Target%20SDK-34-blue)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

## Features

- ✅ Save reminders with title, description, and GPS coordinates (latitude/longitude)
- ✅ Set proximity radius for each reminder (custom distance)
- ✅ View all active reminders in a list interface
- ✅ Toggle reminders on/off
- ✅ Delete reminders
- ✅ **Native Android notifications** when approaching a reminder location
- ✅ **Local storage using Room database** (no external APIs required)
- ✅ Runs in background using LocationMonitorService

## Architecture

The app follows a clean MVP architecture:

```
┌─────────────────────────────────────┐
│  MainActivity + RecyclerView UI     │
└─────────────────────────────────────┘
                  ↕
┌─────────────────────────────────────┐
│  ReminderAdapter + ViewModel        │
└─────────────────────────────────────┘
                  ↕
┌─────────────────────────────────────┐
│  LocationMonitorService             │
│  (Background location tracking)     │
└─────────────────────────────────────┘
                  ↕
┌─────────────────────────────────────┐
│  ReminderRepository                 │
└─────────────────────────────────────┘
                  ↕
┌─────────────────────────────────────┐
│  ReminderDatabase (SQLite/Room)     │
└─────────────────────────────────────┘
```

## Prerequisites

- Android Studio (latest version recommended)
- JDK 17 installed on your system
- macOS/Linux: Use Temurin/OpenJDK 17 from Adoptium
- Android SDK with platform 34

## Build Instructions

### Option 1: Using Gradle Command Line

#### Step 1: Set up Java Development Kit (JDK)

On macOS, install Temurin JDK 17:
```bash
brew install temurin@17
# Or install from adoptium.net and set JAVA_HOME:
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
```

#### Step 2: Configure Android SDK (if not already installed)

```bash
# Install command line tools if needed
sdkmanager "cmdline-tools;latest"
sdkmanager "platforms;android-34"
sdkmanager "build-tools;34.0.0"

# Set ANDROID_HOME environment variable
export ANDROID_HOME=$HOME/Library/Android/sdk
```

#### Step 3: Build Debug APK
```bash
cd /path/to/location_reminder

# Clean and build debug version
gradle clean assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

#### Step 4: Build Release APK (for distribution)

First, create a keystore file:
```bash
mkdir -p keystore

keytool -genkey -v \
  -keystore keystore/location_reminder.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias location_reminder \
  -storepass changeme \
  -keypass changeme \
  -dname "CN=LocationReminder, OU=Development, O=User, L=YourCity, ST=State, C=US"
```

Update `app/build.gradle.kts` with signing configuration:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../keystore/location_reminder.jks")
        storePassword = "REDACTED"  // Change this!
        keyAlias = "location_reminder"
        keyPassword = "REDACTED     // Change this!
    }
}

buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Build release APK:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
gradle clean assembleRelease

# APK location: app/build/outputs/apk/release/app-release.apk
```

### Option 2: Using Android Studio

1. Open the project in Android Studio
2. File → Sync Project with Gradle Files
3. Build → Make Project
4. Run on connected device/emulator

## Installing via ADB

#### Install Release APK
```bash
# Uninstall old version first (if needed)
adb uninstall com.example.locationreminder

# Install release APK
adb install -r app/build/outputs/apk/release/app-release.apk
```

#### Install Debug APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Usage Guide

### Adding a Reminder

1. Launch the app
2. Tap the "+" button to add a new reminder
3. Enter:
   - **Title**: What you want to remember
   - **Description** (optional): Additional details
4. Enter GPS coordinates:
   - **Latitude**: Use Google Maps to get coordinates of your location
   - **Longitude**: Same as above
5. Choose proximity radius (how close you need to be)

### Viewing Reminders

- All reminders are listed below the map view
- Toggle each reminder on/off with the switch
- Delete unwanted reminders

### How It Works

1. **Location Monitoring**: The app runs a background service that checks your location every 5 seconds using Google Play Location Services
2. **Distance Calculation**: Uses Haversine formula to calculate distance from your current position to each reminder's coordinates
3. **Notification Triggered**: When you enter within the proximity radius, a system notification appears with the reminder text
4. **Persistent Storage**: All reminders are stored in SQLite database

## Project Structure

```
location_reminder/
├── app/
│   ├── build.gradle.kts          # App-level build config
│   ├── proguard-rules.pro        # ProGuard rules for release builds
│   └── settings.gradle.kts       # Gradle plugin management
├── gradle/
│   ├── wrapper/                  # Gradle wrapper files
│   └── libs.versions.toml        # Version catalog
├── keystore/                     # Release signing keystore
│   └── location_reminder.jks    # Release APK signature key
├── build.gradle.kts              # Project-level build config
├── settings.gradle.kts           # Project settings
├── local.properties              # Local SDK paths (not committed)
├── gradlew                       # Gradle wrapper script
├── gradlew.bat                   # Gradle wrapper (Windows)
└── README.md                     # This file
```

## Source Code Structure

```
app/src/main/java/com/example/locationreminder/
├── MainActivity.kt                 # Main UI with RecyclerView
├── ReminderAdapter.kt              # Adapter for reminder list
├── data/
│   ├── Reminder.kt                 # Data model (Room entity)
│   └── ReminderDatabase.kt         # SQLite database layer
└── LocationMonitorService.kt       # Background location service
```

## Permissions Required

The app requests these runtime permissions:

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - For tracking user location
- `POST_NOTIFICATIONS` - For showing reminder notifications
- `FOREGROUND_SERVICE` - For running background location monitoring

## Technical Details

### Distance Calculation (Haversine Formula)

```kotlin
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // Earth radius in meters
    
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    
    return (earthRadius * c).div(1000.0) // Result in kilometers
}
```

### Service Architecture

The `LocationMonitorService` implements `ForegroundServiceCompat`:
- Listens to location updates from Fused Location Provider
- Checks proximity to all active reminders every 5 seconds
- Shows notifications when user enters any reminder's radius
- Debounces notifications (same notification once per 5 minutes)

## Dependencies

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Google Play Services - Location
    implementation("com.google.android.gms:play-services-location:21.0+")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

## Notes

- This is an MVP implementation focused on core functionality
- All data is stored locally in the Room database
- No paid APIs are used - everything is open source and free
- Works completely offline once installed
- Minimum supported Android version: 7.0 (API level 24)

## Troubleshooting

### "App cannot be installed due to security concerns"

This warning appears if your app's target SDK doesn't match your device's Android version. To fix:

1. Update `app/build.gradle.kts`:
   ```kotlin
   defaultConfig {
       minSdk = 24
       targetSdk = 34  // Match or exceed device API level
   }
   ```

2. Rebuild and reinstall the APK.

### ADB Installation Fails

The app may already be installed with a different signature. Uninstall first:
```bash
adb uninstall com.example.locationreminder
```

Then reinstall:
```bash
adb install -r /path/to/app-release.apk
```

## License

This project is provided as-is for educational purposes. Feel free to modify and distribute.