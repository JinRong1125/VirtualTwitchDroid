import com.android.build.api.dsl.LibraryExtension
import com.example.virtualtwitchdroid.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Convention for a feature module (Now in Android style). Applies the library +
 * Hilt conventions and wires the dependencies every feature needs: the design system,
 * data + model layers, lifecycle-aware Compose, Hilt-injected ViewModels and navigation.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "virtualtwitchdroid.android.library")
            apply(plugin = "virtualtwitchdroid.hilt")

            extensions.configure<LibraryExtension> {
                testOptions.animationsDisabled = true
            }

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:data"))
                "implementation"(project(":core:model"))
                "implementation"(project(":core:common"))

                "implementation"(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx-navigation-compose").get())

                "implementation"(libs.findLibrary("androidx-compose-material3").get())
                "implementation"(libs.findLibrary("androidx-compose-material-iconsExtended").get())
                "implementation"(libs.findLibrary("androidx-compose-ui").get())
                "implementation"(libs.findLibrary("androidx-compose-ui-graphics").get())
                "implementation"(libs.findLibrary("androidx-compose-foundation").get())
            }
        }
    }
}
