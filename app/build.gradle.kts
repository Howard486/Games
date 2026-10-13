import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// CI passes its run number so every published build installs over the previous one.
val ciBuildNumber: Int? = providers.environmentVariable("CI_BUILD_NUMBER").orNull?.toIntOrNull()

// Shared debug key, injected by CI from GitHub Secrets (never committed: the repo is public).
// Without it, AGP falls back to the machine's ~/.android/debug.keystore.
val sharedDebugKeystore: String? = providers.environmentVariable("DEBUG_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
val sharedDebugKeystorePassword: String? = providers.environmentVariable("DEBUG_KEYSTORE_PASSWORD").orNull

android {
    namespace = "com.howardaistudio.subscriptionhub"
    compileSdk = 36

    defaultConfig {
        // D-03 (proposal): cannot change after the first Play release.
        applicationId = "com.howardaistudio.subscriptionhub"
        // D-02: minSdk 26 gives native java.time and notification channels.
        minSdk = 26
        targetSdk = 36
        versionCode = ciBuildNumber ?: 1
        versionName = "0.1.0" + (ciBuildNumber?.let { "-ci.$it" } ?: "")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (sharedDebugKeystore != null) {
            create("sharedDebug") {
                storeFile = file(requireNotNull(sharedDebugKeystore))
                storePassword = sharedDebugKeystorePassword
                keyAlias = "debug"
                keyPassword = sharedDebugKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            if (sharedDebugKeystore != null) signingConfig = signingConfigs.getByName("sharedDebug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

room {
    // Committed schema history (SA NFR-07); CI fails when it drifts from the entities.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
