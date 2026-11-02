// Phase 0 device probe: a small app that reports what the BYD head unit supports.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional Google Maps SDK key: -PMAPS_API_KEY=... or the MAPS_API_KEY environment variable.
// Without it the Google map test is skipped and the free MapLibre map is still tested.
val mapsApiKey: String = (findProperty("MAPS_API_KEY") as String?)
    ?: System.getenv("MAPS_API_KEY")
    ?: ""

android {
    namespace = "com.selawi.cockpit.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.selawi.cockpit.probe"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_KEY", (mapsApiKey.isNotBlank()).toString())
    }

    signingConfigs {
        // Fixed debug key so each new test build installs over the previous one.
        // Test-only key; the real cockpit release will use a private key.
        getByName("debug") {
            storeFile = file("keystore/probe-debug.jks")
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
    implementation(libs.maplibre)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.base)
}
