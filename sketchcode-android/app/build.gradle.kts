plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sketchcode.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sketchcode.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity + Lifecycle — pinned to 2.7.x to stay compatible with Compose 1.6 BOM
    // (lifecycle 2.8+ moved LocalLifecycleOwner out of compose-ui, breaking setContent)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // OkHttp WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // QR code scanning (CameraX + ML Kit)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // ONNX Runtime with Qualcomm QNN Execution Provider (includes HTP/NPU libs)
    implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.24.1")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
}
