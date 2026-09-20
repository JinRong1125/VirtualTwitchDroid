plugins {
    alias(libs.plugins.virtualtwitchdroid.android.library)
}

android {
    namespace = "com.example.virtualtwitchdroid.core.testing"
}

// A normal (main source set) library so both testImplementation and androidTestImplementation
// consumers can depend on it — the Now in Android :core:testing pattern.
dependencies {
    api(projects.core.data)
    api(projects.core.model)

    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
    api(libs.kotlin.test)
}
