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
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.naderinia.nsa"
        minSdk = 26
        targetSdk = 35

        versionCode = 35
        versionName = "0.26.0"

        // ML Kit ships native libraries for every CPU. Real phones are arm64-v8a or (old ones)
        // armeabi-v7a; x86/x86_64 are only emulators and a few rare tablets. Dropping them is
        // the single biggest APK-size saving.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // The app is Persian (+ English fallback): do not ship the translated strings of every
    // AndroidX / Material / ML Kit library in 80+ other languages.
    androidResources {
        localeFilters += listOf("fa", "en")
    }

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
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
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
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/com/android/build/gradle/app-metadata.properties",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
        // Store native libraries compressed inside the APK. The APK file you share gets clearly
        // smaller (they are extracted on install, so installed size is a bit larger).
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {

    // AndroidX
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // App Lock - PIN / Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.8")

    // Encrypted SharedPreferences (نگه داشته شده روی stable)
    implementation("androidx.security:security-crypto:1.0.0")

    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // ML Kit pulls in exifinterface 1.0.0 (old, often missing from local caches/mirrors).
    // Coil used to bump it to 1.3.7 behind our back; now we pin that version ourselves.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Unit tests
    testImplementation("junit:junit:4.13.2")

    // Instrumentation tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
