plugins {
    alias(libs.plugins.virtualtwitchdroid.android.library)
    alias(libs.plugins.virtualtwitchdroid.hilt)
}

android {
    namespace = "com.example.virtualtwitchdroid.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
