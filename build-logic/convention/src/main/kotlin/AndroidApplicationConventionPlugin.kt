import com.android.build.api.dsl.ApplicationExtension
import com.example.virtualtwitchdroid.configureKotlinAndroid
import com.example.virtualtwitchdroid.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 37
                testOptions.animationsDisabled = true
                // The app aggregates lint across all library modules it depends on, so
                // `:app:lintDebug` is a single whole-project gate for the dev loop. The baseline
                // lives here (app-only) — see the note in configureLint.
                lint {
                    configureLint(this)
                    checkDependencies = true
                    baseline = file("lint-baseline.xml")
                }
            }
        }
    }
}
