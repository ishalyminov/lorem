# Location Reminder - Build and Deploy Instructions

This document contains step-by-step instructions for building and deploying the Location Reminder Android app.

## Prerequisites

- JDK 17 installed at `/usr/bin/java` (or path specified via JAVA_HOME)
- Android SDK with platform 35 installed
- ADB installed

### Step 1: Set up Java Development Kit (JDK)

```bash
export JAVA_HOME=/usr
# or use your preferred JDK path
```

### Step 2: Configure Android SDK

```bash
sdkmanager "cmdline-tools;latest"
sdkmanager "platforms;android-35"
sdkmanager "build-tools;34.0.0"
export ANDROID_HOME=/home/ishalyminov/android-sdk
```

## Build Instructions

### Option 1: Using Direct Gradle (Recommended)

If the gradle-8.6 directory does not exist, first run:

```bash
cd /path/to/location_reminder
./scripts/setup_gradle.sh
```

#### Build Debug APK
```bash
export JAVA_HOME=/usr
export ANDROID_HOME=/home/ishalyminov/android-sdk
./gradle-8.6/bin/gradle clean assembleDebug --no-daemon

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

#### Build Release APK (for distribution)

Build release APK:
```bash
export JAVA_HOME=/usr
export ANDROID_HOME=/home/ishalyminov/android-sdk
./gradle-8.6/bin/gradle clean assembleRelease --no-daemon

# APK location: app/build/outputs/apk/release/app-release.apk
```

## Installation Instructions

### Install Release APK to Device
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Commit Changes (Do NOT commit gradle-8.6/)

After building and testing, if there are any project source code changes:

```bash
git add .
# DO NOT commit gradle-8.6/ - it will be regenerated automatically using scripts/setup_gradle.sh
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
tail -50 app/build.gradle.kts