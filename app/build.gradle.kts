// Selawi Cockpit: the full-screen car dashboard.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Renders screens to PNG on the build machine, so the design can be reviewed without the car.
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.selawi.cockpit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.selawi.cockpit"
        // The Seal 05 runs Android 12 (API 32); 26 keeps older BYD units installable.
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.2.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Test key shared with the probe so every test build installs over the previous one.
        // A private key replaces it before any public release.
        getByName("debug") {
            storeFile = file("../probe/keystore/probe-debug.jks")
            storePassword = "android"
            keyAlias = "probe"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.maplibre)
}
