import java.util.Properties

plugins {
    id("com.android.application") version "8.2.1"
    id("org.jetbrains.kotlin.android") version "1.9.20"
}

// Load Maps API key from properties file
val mapsApiKey: String by lazy {
    val keyFile = file("../keystore/maps_api_key.properties")
    if (keyFile.exists()) {
        val props = Properties()
        keyFile.inputStream().use { props.load(it) }
        props.getProperty("GOOGLE_MAPS_API_KEY", "")
    } else {
        ""
    }
}

android {
    namespace = "com.example.locationreminder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.locationreminder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Inject Maps API key into manifest
        manifestPlaceholders["googleMapsApiKey"] = mapsApiKey
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        aidl = false
        renderScript = false
        compose = false
        resValues = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/location_reminder_new.jks")
            storePassword = "REDACTED"
            keyAlias = "location_reminder"
            keyPassword = "REDACTED"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }

        debug {
            isDebuggable = true
            isPseudoLocalesEnabled = true
        }
    }

    dependencies {
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.11.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("androidx.activity:activity-ktx:1.8.2")

        // RecyclerView - required for our UI
        implementation("androidx.recyclerview:recyclerview:1.3.2")
        implementation("androidx.cardview:cardview:1.0.0")

        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        // Google Play Services - Location
        implementation("com.google.android.gms:play-services-location:21.0.+")

        // Google Maps SDK
        implementation("com.google.android.gms:play-services-maps:19.0.+")
    }
}
