package com.example.virtualtwitchdroid.core.designsystem

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Enforces the "all user-facing UI copy lives in strings.xml" rule (neither Spotless nor Android Lint
 * checks hardcoded text in Compose). Scans the app + feature + designsystem main sources and fails if
 * a `Text("…")`, `text = "…"`, or `contentDescription = "…"` uses a hardcoded literal whose STATIC text
 * (after stripping `$x` / `${…}` interpolations) still contains a word — those must be
 * `stringResource(R.string.…)`. Purely-dynamic values like `Text("  $uptime")` or `" ${count}"` are
 * allowed (no translatable words). Zero-dependency source scan, same style as the other guard tests.
 */
class NoHardcodedUiStringTest {

    @Test
    fun uiCode_usesStringResources_notHardcodedText() {
        var repoRoot = File(".").absoluteFile
        while (!File(repoRoot, "settings.gradle.kts").exists()) {
            repoRoot = repoRoot.parentFile ?: break
        }
        val sourceRoots = listOf(
            File(repoRoot, "app"),
            File(repoRoot, "feature"),
            File(repoRoot, "core/designsystem"),
        )
        assertTrue(
            sourceRoots.all { it.isDirectory },
            "could not locate UI source roots under ${repoRoot.absolutePath}",
        )

        // A Text(...) positional literal, a `text = "…"`, or a `contentDescription = "…"`.
        val callSite = Regex("""(?:Text\(\s*|text\s*=\s*|contentDescription\s*=\s*)"((?:\\.|[^"\\])*)"""")
        val interpolation = Regex("""\$\{[^}]*}|\$\w+""")
        val word = Regex("""[A-Za-z]{2,}""")

        val offenders = sourceRoots.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filter { "${File.separator}build${File.separator}" !in it.path }
            .flatMap { file ->
                file.readLines().withIndex().mapNotNull { (i, line) ->
                    val literal = callSite.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                    val static = literal.replace(interpolation, "")
                    if (word.containsMatchIn(static)) {
                        "${file.relativeTo(repoRoot).path}:${i + 1}: \"$literal\""
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "hardcoded user-facing text found — move it to strings.xml and use stringResource():\n" +
                offenders.joinToString("\n"),
        )
    }
}
