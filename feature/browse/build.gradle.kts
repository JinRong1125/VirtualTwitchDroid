plugins {
    alias(libs.plugins.virtualtwitchdroid.android.feature)
    alias(libs.plugins.virtualtwitchdroid.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.virtualtwitchdroid.feature.browse"
}

dependencies {
    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core) // pin newer Espresso for API 37+
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
