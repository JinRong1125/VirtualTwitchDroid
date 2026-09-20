package com.example.virtualtwitchdroid

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Configure base Kotlin with Android options. Mirrors Now in Android's convention:
 * a single place that pins compileSdk / minSdk, Java version, desugaring and Kotlin options.
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = 37

        defaultConfig.apply {
            minSdk = 24
        }

        compileOptions.apply {
            // Up to Java 11 APIs are available through desugaring.
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
            isCoreLibraryDesugaringEnabled = true
        }
    }

    configureKotlin<KotlinAndroidProjectExtension>()

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("android.desugarJdkLibs").get())
    }
}

/**
 * Shared Android Lint policy for every module: fail the build on lint ERRORS and share one root
 * rule config (`lint.xml`). `warningsAsErrors` mirrors the Kotlin compiler flag. Applied on the
 * concrete Application/Library extensions (their `lint` block resolves, unlike the raw
 * [CommonExtension] used by [configureKotlinAndroid]).
 *
 * The baseline is intentionally NOT set here: it lives only on the app module (see
 * `AndroidApplicationConventionPlugin`), whose `checkDependencies = true` makes `:app:lintDebug` the
 * single whole-project gate. Putting a `baseline = file(...)` in this shared helper would make every
 * library module expect its own (non-existent) baseline and abort `./gradlew check`/`build`/`lint`.
 */
internal fun Project.configureLint(lint: Lint) {
    lint.warningsAsErrors =
        providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false).get()
    lint.abortOnError = true
    lint.checkReleaseBuilds = false
    val sharedConfig = rootProject.file("lint.xml")
    if (sharedConfig.exists()) lint.lintConfig = sharedConfig
}

/**
 * Configure base Kotlin options for JVM (non-Android) modules such as :core:model.
 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    configureKotlin<KotlinJvmProjectExtension>()
}

private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> error("Unsupported project extension $this ${T::class}")
    }.apply {
        jvmTarget = JvmTarget.JVM_11
        allWarningsAsErrors = warningsAsErrors
        // Apply annotations (e.g. the @Dispatcher qualifier) to both the constructor value
        // parameter and its property — the forthcoming Kotlin default; silences the K2 warning.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
