# Location Reminder - Build and Deploy Instructions

This document contains step-by-step instructions for building and deploying the Location Reminder Android app.

## Prerequisites

- JDK 17 installed and JAVA_HOME configured
- Android SDK with platform 34
- ADB installed

### Step 1: Set up Java Development Kit (JDK)

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/17.*
# or use your preferred JDK path
```

### Step 2: Configure Android SDK

```bash
sdkmanager "cmdline-tools;latest"
sdkmanager "platforms;android-34"
sdkmanager "build-tools;34.0.0"
export ANDROID_HOME=$HOME/Library/Android/sdk
```

## Build Instructions

### Option 1: Using Gradle Command Line

#### Build Debug APK
```bash
cd /path/to/location_reminder

gradle clean assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

#### Build Release APK (for distribution)

First, create a keystore file if you haven't already:

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

Update `app/build.gradle.kts` with signing configuration if needed (see README.md for details).

Build release APK:
```bash
gradle clean assembleRelease

# APK location: app/build/outputs/apk/release/app-release.apk
```

## Installation Instructions

### Option 1: Using Gradlew Wrapper

This is the simplest method. Navigate to the project directory and run:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Option 2: WSL + Windows ADB (for developers using WSL)

If you're running this in WSL and have installed adb.exe on Windows, use the install script:

```bash
bash install_apk.sh
```

This script handles the path conversion correctly for WSL environments.

### Option 3: Direct Gradle Commands

#### Clean and Build Debug APK
```bash
gradlew clean assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

#### Clean and Build Release APK
```bash
gradlew clean assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Commit Changes

After building and testing, commit your changes:

```bash
git add .
git commit -m "feat: build and deploy latest version"
```

## Push to Remote Repository

Push the changes to GitHub:

```bash
git push origin main
```

## Verification Steps

Before pushing, verify your APK is valid:

```bash
# Check APK exists
ls -la app/build/outputs/apk/release/app-release.apk

# View build output for any errors
tail -50 gradle/app/build.gradle.kts
```

## Quick Deploy Script

Run this command to deploy from WSL with Windows adb:

```bash
./install_apk.sh
```

Or with Gradlew wrapper (cross-platform):

```bash
./gradlew assembleRelease && adb install app/build/outputs/apk/release/app-release.apk -r