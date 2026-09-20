plugins {
    alias(libs.plugins.virtualtwitchdroid.android.feature)
    alias(libs.plugins.virtualtwitchdroid.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.virtualtwitchdroid.feature.avatar"
}

dependencies {
    // Face tracking: MediaPipe Face Landmarker (blendshapes + head matrix) fed by CameraX ImageAnalysis.
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.activity.compose) // rememberLauncherForActivityResult (camera permission)

    // Avatar rendering: Filament + gltfio load the VRM (it is glTF); the VRM extension JSON is parsed here.
    // (filament-utils' ModelViewer is not used: the renderer owns its scene so it can draw into the
    // broadcast encoder's Surface as well as a TextureView.)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core) // pin newer Espresso for API 37+
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
