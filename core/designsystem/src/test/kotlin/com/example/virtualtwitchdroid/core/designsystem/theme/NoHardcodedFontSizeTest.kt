package com.example.virtualtwitchdroid.core.designsystem.theme

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Enforces the design-token typography rule (from the `virtualtwitchdroid-coding-style` skill) that neither
 * Spotless/ktlint nor Android Lint can check: UI code must size text via `MaterialTheme.typography.*`,
 * never a hardcoded `fontSize = N.sp`. Scans the feature + designsystem main sources and fails if any
 * literal `fontSize = <number>` is reintroduced. `Type.kt` (where the scale is defined) is exempt.
 */
class NoHardcodedFontSizeTest {

    @Test
    fun uiCode_usesTypographyTokens_notHardcodedFontSizes() {
        // Walk up from the module dir to the repo root (the dir that owns settings.gradle.kts).
        var repoRoot: File = File(".").absoluteFile
        while (!File(repoRoot, "settings.gradle.kts").exists()) {
            repoRoot = repoRoot.parentFile ?: break
        }
        val sourceRoots = listOf(File(repoRoot, "feature"), File(repoRoot, "core/designsystem"))
        // Guard against a vacuous pass if path resolution ever breaks.
        assertTrue(
            sourceRoots.all { it.isDirectory },
            "could not locate UI source roots under ${repoRoot.absolutePath}",
        )

        val hardcoded = Regex("""fontSize\s*=\s*\d""")
        val offenders = sourceRoots.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filter { "${File.separator}build${File.separator}" !in it.path }
            .filter { it.name != "Type.kt" }
            .filter { hardcoded.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repoRoot).path }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "hardcoded fontSize found — use MaterialTheme.typography.* instead: $offenders",
        )
    }
}
