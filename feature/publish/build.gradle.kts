plugins {
    alias(libs.plugins.virtualtwitchdroid.android.feature)
    alias(libs.plugins.virtualtwitchdroid.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.virtualtwitchdroid.feature.publish"
}

dependencies {
    // RootEncoder (camera→RTMP): one always-running GL pipeline with attachable previews — see
    // PublishController/CameraPreview. Replaced StreamPack (whose fixed-viewport GL preview couldn't
    // scale the floating mini without a flash).
    implementation(libs.rootencoder)

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Instrumented Compose UI tests: drive stateless composables with mock/fake data and assert via
    // semantics — the way we verify live-only UI (e.g. the received-chat overlay) that ARTEMIS can't
    // reach on-device without a real broadcast. Mirrors :feature:browse / :feature:stream.
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core) // pin newer Espresso for API 37+
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
