plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.rocketwallpaper"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            // Fixed debug key checked into the repo so every build (local or
            // via GitHub Actions) signs with the SAME key. Without this,
            // each CI run generates a random key and re-installing a newer
            // APK over an older one silently fails with a signature
            // mismatch.
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.example.rocketwallpaper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
