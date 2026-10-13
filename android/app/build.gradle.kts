plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kreativekoala.cleanup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kreativekoala.smartspace"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials live outside the repo, in ~/.gradle/gradle.properties:
            // ANDROID_UPLOAD_STORE_FILE, ANDROID_UPLOAD_STORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD
            val uploadStore = providers.gradleProperty("ANDROID_UPLOAD_STORE_FILE").orNull
            if (uploadStore != null) {
                storeFile = file(uploadStore)
                storePassword = providers.gradleProperty("ANDROID_UPLOAD_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("ANDROID_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("ANDROID_UPLOAD_KEY_PASSWORD").get()
            } else if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
                throw GradleException("Release signing needs ANDROID_UPLOAD_* in ~/.gradle/gradle.properties (see build.gradle.kts).")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // AppCompat (for per-app language support)
    implementation(libs.appcompat)

    // Activity & Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media3 (Video Compression)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)

    // Google Play Billing
    implementation(libs.billing)

    // Coroutines
    implementation(libs.coroutines.android)

    // Biometric
    implementation(libs.biometric)

    // Security
    implementation(libs.security.crypto)

    // Glance Widgets
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Google Sign-In
    implementation(libs.google.signin)

    // Play In-App Review
    implementation(libs.play.review)

    // Splash Screen
    implementation(libs.splashscreen)

    // Core KTX
    implementation(libs.core.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
