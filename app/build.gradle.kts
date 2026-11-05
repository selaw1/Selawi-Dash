// Selawi Dash: the full-screen car dashboard.
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
        versionName = "0.3.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        // 64-bit only: the head units this is for are 64-bit, and the 32-bit copy of the 3D engine
        // would add about 6 MB.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        // Fixed test key, so every build installs over the previous one and keeps the driver's
        // settings. A private key replaces it before any public release.
        getByName("debug") {
            storeFile = file("../keystore/debug.jks")
            storePassword = "android"
            keyAlias = "probe"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        // What drivers install: unused code and resources removed, so it is small enough for the
        // download page and runs faster. Same key, so it installs over earlier builds.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        // Store the 3D engine's native libraries compressed; Android unpacks them once on install.
        jniLibs { useLegacyPackaging = true }
    }

    androidResources {
        // The car model is already compressed; storing it as-is keeps it fast to read.
        noCompress += "glb"
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
    implementation(libs.filament.android)
    implementation(libs.gltfio.android)
}
