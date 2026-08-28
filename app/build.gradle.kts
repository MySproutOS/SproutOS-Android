plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "me.sproutos.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.sproutos.client"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        debug {
            // Emulator traffic to 10.0.2.2 is plain HTTP. Release builds stay HTTPS-only.
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        // Unsigned. SproutOS signs every APK it distributes, including this one, on a machine that
        // is not a CI runner — see docs/apk-signing.md in the platform repository.
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-ktx:1.15.0")
    // Custom Tabs: the system browser, in this app's window. A WebView here could read the password
    // as it is typed, which is why every OAuth guideline for native apps forbids one.
    implementation("androidx.browser:browser:1.8.0")
    // Keys in the Android keystore, so the token is not readable from a backup or an adb pull.
    implementation("androidx.security:security-crypto:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
