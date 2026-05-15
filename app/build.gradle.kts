plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.voicellm"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.doggedmaster.kern"
        minSdk = 29          // RECORD_AUDIO + AudioRecord + AAudio
        targetSdk = 35
        versionCode = 57
        versionName = "0.4.17"

        // LiteRT-LM + sherpa-onnx ship arm64 prebuilt libs.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("debugKey") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debugKey")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "kern-${variant.versionName}.apk"
            }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0")
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    // --- AndroidX / Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- On-device LLM (LiteRT-LM) ---
    // Lädt .litertlm-Modelle (Gemma 4 E2B). GPU-Backend inklusive.
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    // --- TTS: sherpa-onnx ---
    implementation(fileTree("libs") { include("*.aar") })

    // --- Archiv-Extraktion (tar.bz2 fuer Modell-Downloads) ---
    implementation("org.apache.commons:commons-compress:1.27.1")

    // --- Hintergrund-Downloads ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
