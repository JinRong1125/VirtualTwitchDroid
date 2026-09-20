@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // RootEncoder (com.github.pedroSG94.RootEncoder)
        // VOICEVOX CORE's Android Java package is not published to Maven Central; it is vendored from the
        // official release asset (third_party/voicevox/README.md) and served only for its own group.
        maven {
            url = uri("third_party/voicevox/m2")
            content { includeGroup("jp.hiroshiba.voicevoxcore") }
        }
        // sherpa-onnx's Android AAR is not on Maven Central either (third_party/sherpa-onnx/README.md); a local
        // Maven layout (not a direct .aar file) so the library module can still be bundled as an AAR.
        maven {
            url = uri("third_party/sherpa-onnx/m2")
            content { includeGroup("com.k2fsa.sherpa.onnx") }
        }
    }
}

// Type-safe project accessors, e.g. projects.core.data (Now in Android style)
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "VirtualTwitchDroid"

include(":app")
include(":baselineprofile")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:data")
include(":core:testing")

include(":feature:browse")
include(":feature:stream")
include(":feature:publish")
include(":feature:avatar")
include(":feature:voice")
