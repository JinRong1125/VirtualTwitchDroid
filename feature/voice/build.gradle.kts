plugins {
    alias(libs.plugins.virtualtwitchdroid.android.library)
    alias(libs.plugins.virtualtwitchdroid.hilt)
}

android {
    namespace = "com.example.virtualtwitchdroid.feature.voice"
}

dependencies {
    // The seams the publish and avatar features consume (SpeechStreamSource, MouthTrackSource).
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)

    // Japanese speech recognition: sherpa-onnx (Silero VAD + ReazonSpeech k2 v2 Zipformer, offline). The
    // AAR is vendored as a local Maven repo — see third_party/sherpa-onnx/README.md.
    implementation(libs.sherpa.onnx)

    // Japanese TTS with the ずんだもん voice: VOICEVOX CORE (vendored local Maven repo, MIT). Its ONNX
    // runtime (libvoicevox_onnxruntime.so) lives in src/main/jniLibs.
    implementation(libs.voicevox.core.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
