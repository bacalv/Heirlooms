plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose compiler bundled with Kotlin 2.0+; replaces kotlinCompilerExtensionVersion.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "digital.heirlooms.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "digital.heirlooms.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 59
        versionName = "0.53.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL_OVERRIDE", "\"\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Heirlooms Test")
            buildConfigField("String", "BASE_URL_OVERRIDE", "\"https://test.api.heirlooms.digital\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "META-INF/BC*.DSA"
        resources.excludes += "META-INF/BC*.SF"
        resources.excludes += "META-INF/BCEL.SF"
        resources.excludes += "META-INF/BCEL.DSA"
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Compose BOM — pins all Compose library versions consistently.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle ViewModel + runtime for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Coil — image loading. coil-network-okhttp provides the OkHttp fetcher for remote URLs.
    // coil-video provides VideoFrameDecoder for showing the first frame of local video URIs.
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("io.coil-kt.coil3:coil-video:3.1.0")

    // ExoPlayer — inline video playback in photo detail.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // BouncyCastle — Argon2id for passphrase key derivation.
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    // EXIF orientation reading for correct image display on Android.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // QR code generation (DevicesAccessScreen) + camera scanning (PairingScreen)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Jetpack Security — EncryptedSharedPreferences backed by Android Keystore AES-GCM.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric — BiometricPrompt for vault gate (SEC-015).
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.79")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.zxing:core:3.5.3")
    // Provides a real org.json implementation for JVM unit tests (Android SDK stubs are mocked)
    testImplementation("org.json:json:20231013")

    // Compose UI tests (instrumented — require device or emulator).
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Adds the test activity required by createComposeRule() to the debug APK manifest.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
