# Location Reminder - Build and Deploy Instructions

## Build Release APK

```bash
export JAVA_HOME=/usr
export ANDROID_HOME=/home/ishalyminov/android-sdk
./gradle-8.6/bin/gradle clean assembleRelease --no-daemon
```

APK location: `app/build/outputs/apk/release/app-release.apk`

## Install on Device

```bash
/mnt/c/Users/ishalyminov/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r app/build/outputs/apk/release/app-release.apk
```

## Commit Changes

```bash
git add .
git commit -m "feat: build and deploy latest version"
git push origin main
```

**Note:** Do NOT commit `gradle-8.6/` directory - it will be regenerated automatically using `scripts/setup_gradle.sh`