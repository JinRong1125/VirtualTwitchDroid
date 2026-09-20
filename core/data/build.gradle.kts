plugins {
    alias(libs.plugins.virtualtwitchdroid.android.library)
    alias(libs.plugins.virtualtwitchdroid.hilt)
}

android {
    namespace = "com.example.virtualtwitchdroid.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.network)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
