plugins {
    alias(libs.plugins.virtualtwitchdroid.android.library)
    alias(libs.plugins.virtualtwitchdroid.android.library.compose)
}

android {
    namespace = "com.example.virtualtwitchdroid.core.designsystem"
}

dependencies {
    api(projects.core.model)

    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)

    api(libs.coil.kt.compose)
    api(libs.coil.network.okhttp)

    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core) // pin newer Espresso for API 37+
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
