package com.example.virtualtwitchdroid.core.designsystem

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Architecture guard (the `virtualtwitchdroid-architecture` rules that neither Lint nor Spotless enforce),
 * as a zero-dependency source scan — the same style as `NoHardcodedFontSizeTest`. Deterministic and
 * compatible with any toolchain (no Konsist/Kotlin-parser dependency to keep in lockstep with the
 * Kotlin version). Runs in the unit phase of `scripts/ai-dev-loop.sh`.
 *
 * Guarded rule: a **feature module must not depend on another feature module** — features compose only
 * at the `:app` layer, so `:feature:stream` importing `com.example.virtualtwitchdroid.feature.publish.*`
 * (etc.) is an architecture violation that erodes modularity.
 */
class ArchitectureGuardTest {

    private fun repoRoot(): File {
        var dir: File = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return dir
    }

    @Test
    fun noFeatureModuleDependsOnAnotherFeatureModule() {
        val root = repoRoot()
        val featureDir = File(root, "feature")
        assertTrue(featureDir.isDirectory, "could not locate the feature/ modules under ${root.absolutePath}")

        val ownerRegex = Regex("""${File.separator}feature${File.separator}(\w+)${File.separator}""")
        val importRegex = Regex("""^\s*import\s+com\.example\.virtualtwitchdroid\.feature\.(\w+)""")

        val violations = featureDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filter { "${File.separator}build${File.separator}" !in it.path }
            .flatMap { file ->
                val owner = ownerRegex.find(file.path)?.groupValues?.get(1)
                file.readLines().mapNotNull { line ->
                    val imported = importRegex.find(line)?.groupValues?.get(1)
                    if (owner != null && imported != null && imported != owner) {
                        "${file.relativeTo(root).path}: :feature:$owner imports :feature:$imported"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "feature→feature dependency is forbidden (features compose only at :app):\n" +
                violations.joinToString("\n"),
        )
    }
}
