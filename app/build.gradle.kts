
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ir.naderinia.nsa"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.naderinia.nsa"
        minSdk = 26
        targetSdk = 34

        versionCode = 18
        versionName = "0.15.2"
    }

    /*
     * Release signing
     *
     * Signing credentials are stored in keystore.properties.
     * This file must NOT be committed to Git.
     *
     * Example:
     * storeFile=/path/to/nsa-release.jks
     * storePassword=...
     * keyAlias=...
     * keyPassword=...
     */
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()

    val hasSigningConfig = keystorePropertiesFile.exists()

    if (hasSigningConfig) {
        FileInputStream(keystorePropertiesFile).use {
            keystoreProperties.load(it)
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(
                    keystoreProperties["storeFile"] as String
                )

                storePassword =
                    keystoreProperties["storePassword"] as String

                keyAlias =
                    keystoreProperties["keyAlias"] as String

                keyPassword =
                    keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            /*
             * Production release build:
             * - R8 / code shrinking enabled
             * - Resource shrinking enabled
             * - Release signing applied when keystore.properties exists
             */
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Jetpack Compose
    implementation(
        platform("androidx.compose:compose-bom:2024.06.00")
    )
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    // Navigation
    implementation(
        "androidx.navigation:navigation-compose:2.7.7"
    )

    // Room - Local database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Kotlin Coroutines
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
    )

    // App Lock - PIN / Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // ML Kit - Offline text recognition
    implementation(
        "com.google.mlkit:text-recognition:16.0.0"
    )

    // Coil - Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")

    // Instrumentation tests
    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.6.1"
    )
}
