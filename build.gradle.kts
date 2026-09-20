import com.diffplug.gradle.spotless.SpotlessExtension

// Top-level build file. Plugins are declared here (apply false) so that their
// classpath is available to sub-projects, which opt in via the convention plugins.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

// Spotless + ktlint: the formatting gate for every module of the MAIN build. ktlint reads the root
// .editorconfig (indent, 120 cols, no-wildcard-imports, Compose function-naming exemptions), so
// `spotlessApply` formats and `spotlessCheck` (wired into scripts/ai-dev-loop.sh) fails the loop on
// unformatted code. NOTE: the `build-logic` composite build is a separate Gradle build, so its
// convention-plugin sources are NOT covered here — wiring Spotless there is a follow-up.
val ktlintVersion = libs.versions.ktlint.get()
subprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }
}
