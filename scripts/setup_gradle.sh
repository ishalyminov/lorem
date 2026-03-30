#!/bin/bash
# Script to download and set up Gradle 8.6

GRADLE_VERSION="8.6"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIR="./gradle-${GRADLE_VERSION}"

echo "Downloading Gradle ${GRADLE_VERSION}..."
mkdir -p scripts
cd scripts
wget -O gradle-${GRADLE_VERSION}-bin.zip "$GRADLE_URL"

echo "Extracting Gradle..."
unzip gradle-${GRADLE_VERSION}-bin.zip -d $GRADLE_DIR

rm gradle-${GRADLE_VERSION}-bin.zip

echo "Gradle setup complete!"