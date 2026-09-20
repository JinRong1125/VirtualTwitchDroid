plugins {
    alias(libs.plugins.virtualtwitchdroid.android.library)
    alias(libs.plugins.virtualtwitchdroid.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.virtualtwitchdroid.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
