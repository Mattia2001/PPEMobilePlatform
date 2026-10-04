plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Keep the production default stable while allowing an explicitly version-matched
// LiteRT/MediaTek diagnostic build (for example -PppeLiteRtVersion=2.1.0rc1).
val ppeLiteRtVersion = providers.gradleProperty("ppeLiteRtVersion").orElse("2.1.5")

android {
    namespace = "it.polito.ppemobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "it.polito.ppemobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "tflite"
    }
    packaging {
        // LiteRT discovers compiler/dispatch plugins by scanning nativeLibraryDir.
        // They must therefore be extracted from the APK instead of mmap'ed in place.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.google.ai.edge.litert:litert:${ppeLiteRtVersion.get()}")
    if (ppeLiteRtVersion.get().contains("rc")) {
        // The 2.1.0 RC CompiledModel artifact did not yet bundle the legacy
        // Interpreter API used by the application's explicit CPU fallback.
        implementation("org.tensorflow:tensorflow-lite-api:2.17.0")
        implementation("org.tensorflow:tensorflow-lite:2.17.0")
    }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Diagnostic-only native API access; never packaged in the user application.
    androidTestImplementation("net.java.dev.jna:jna:5.14.0@aar")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
