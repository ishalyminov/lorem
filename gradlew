#!/bin/bash
# Gradle wrapper for Android builds

APP_DIR="$(cd "$(dirname "$0")" && pwd)"

# Set Android SDK environment variables (allow override)
if [ -z "$ANDROID_HOME" ]; then
    ANDROID_HOME="/mnt/c/Users/ishalyminov/AppData/Local/Android/Sdk"
fi
if [ -z "$ANDROID_SDK_ROOT" ]; then
    ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

# Find Java executable
JAVA_BIN="$(which java 2>/dev/null || echo '')"
if [ -z "$JAVA_BIN" ]; then
    echo "ERROR: Java not found. Please install Java."
    exit 1
fi

# Check for existing local gradle or download it if needed
GRADLE_VER="8.6"
GRADLE_DIR="$APP_DIR/gradle-${GRADLE_VER}"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"

if [ -x "$GRADLE_BIN" ]; then
    echo "Using local Gradle at $GRADLE_BIN"
elif [ ! -f "$GRADLE_VER.zip" ]; then
    echo "Downloading Gradle ${GRADLE_VER}..."
    if command -v wget &> /dev/null; then
        curl -L --silent "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip" -o "$GRADLE_VER.zip" && \
            unzip -q "$GRADLE_VER.zip" -d "$APP_DIR" && \
            rm "$GRADLE_VER.zip" && \
            echo "Gradle ${GRADLE_VER} downloaded and installed."
    else
        curl -L --silent "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip" -o "$GRADLE_VER.zip" && \
            unzip -q "$GRADLE_VER.zip" -d "$APP_DIR" && \
            rm "$GRADLE_VER.zip" && \
            echo "Gradle ${GRADLE_VER} downloaded and installed."
    fi
fi

if [ -x "$GRADLE_BIN" ]; then
    exec env ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" "$GRADLE_BIN" --no-daemon --stacktrace "$@"
else
    echo "ERROR: Gradle not found. Please check your installation."
    exit 1
fi