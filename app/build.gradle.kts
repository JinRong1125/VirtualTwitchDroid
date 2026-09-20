plugins {
    alias(libs.plugins.virtualtwitchdroid.android.application)
    alias(libs.plugins.virtualtwitchdroid.android.application.compose)
    alias(libs.plugins.virtualtwitchdroid.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.example.virtualtwitchdroid"

    defaultConfig {
        applicationId = "com.example.virtualtwitchdroid"
        versionCode = 1
        versionName = "1.0"
        // The Zundamon voice's VOICEVOX CORE ships only 64-bit libraries; sherpa-onnx also carries 32-bit
        // ones, and a 32-bit install would UnsatisfiedLinkError on VOICEVOX. x86_64 keeps the emulator working.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    packaging {
        // VOICEVOX CORE's Java package pulls in jakarta.annotation-api + jakarta.validation-api, which both
        // ship META-INF/NOTICE.md and LICENSE.md; keep one copy instead of failing the merge.
        resources.pickFirsts += listOf("META-INF/NOTICE.md", "META-INF/LICENSE.md")
    }

    androidResources {
        // The bundled voice models (feature/voice/src/main/assets/voice/) are already int8 / archived, so
        // compressing them again only slows the build and can grow the APK; store them raw. `ModelStore`
        // streams each out to filesDir on first launch, so a fresh clone needs no download.
        noCompress += listOf("onnx", "vvm", "tgz")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sample app: debug-sign release so the Baseline Profile plugin's generated
            // `nonMinifiedRelease` / `benchmarkRelease` variants install on a device without a
            // release keystore. Replace with a real signingConfig before shipping.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.feature.browse)
    implementation(projects.feature.stream)
    implementation(projects.feature.publish)
    implementation(projects.feature.avatar)
    implementation(projects.feature.voice)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.navigation.compose)

    // ART installs this app's Baseline Profile at install time (profileinstaller), and the profile
    // itself is produced by the :baselineprofile module and packaged via this dependency.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)

    // Dev-time memory-leak monitoring (debug only, never in release). Auto-installs via its own
    // ContentProvider — no code needed; heap analyses land in logcat for the ARTEMIS leak scan.
    debugImplementation(libs.leakcanary.android)
}
