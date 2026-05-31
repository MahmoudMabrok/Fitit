plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "tools.mo3ta.fitit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "tools.mo3ta.fitit"
        minSdk = 24
        targetSdk = 36
        versionCode = 49
        versionName = "2.5.2.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en", "ar")
        // .tflite models must stay uncompressed so the LiteRT Interpreter can memory-map them.
        noCompress += "tflite"
    }

    bundle {
        language {
            // Specifies that language resources should be packaged
            // with the base and dynamic feature APKs, preventing splitting.
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            // Return default values (instead of throwing) for un-mocked
            // android.* framework calls reached from local unit tests.
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        release {
            // Enable R8 code minification (shrinks, obfuscates, and optimizes code)
            isMinifyEnabled = true
            // Enable resource shrinking (removes unused resources)
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    // litert-gpu-api holds GpuDelegateFactory (the GpuDelegate ctor's parameter type); it is not
    // pulled transitively by litert-gpu, so the GPU delegate code won't compile without it.
    implementation(libs.litert.gpu.api)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.google.firebase:firebase-analytics:22.2.0")
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
