plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Each GitHub Actions build passes its run number in (-PbuildNumber=N), so every build
// gets its own version: 1.0.N. Builds on your own machine fall back to 1.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.benclift.sortedapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.benclift.sortedapp"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // One fixed signing key for every build, so Android lets a new version install over the old one.
    // The key is deliberately kept in the repo (this is a personal sideloaded app, not a Play Store app).
    signingConfigs {
        create("shared") {
            storeFile = file("sortedapp-key.p12")
            storeType = "pkcs12"
            storePassword = "sortedapp"
            keyAlias = "sorted"
            keyPassword = "sortedapp"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
