import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.keepsakeai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keepsakeai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Fixed debug key (committed) so every CI build can install over the last one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
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
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room Local Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager for Daily Mind Suggest
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Encrypted storage for BYOK API keys
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking for Multi-Model Mind Pilot (Gemini, OpenAI, Perplexity)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // On-device OCR (ML Kit)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // CameraX for OSee-style Live Visual Scanning
    val cameraXVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")


    // Jetpack Glance for Android Home-Screen Widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")


    // Article & Link Content Extraction (Long-doc & YouTube/TikTok Capture)
    implementation("org.jsoup:jsoup:1.18.1")

    // Biometric App Lock (Fingerprint / Face Unlock)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Coil for Photo + Note Memory Card thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")

    // UPI QR codes for bill splitting
    implementation("com.google.zxing:core:3.5.3")

    // Vector map view: MapLibre (BSD) drawing free OpenFreeMap tiles, no API key
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Email receipts, orders, tickets and bills over IMAP (Gmail, Yahoo, others) with app passwords
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
