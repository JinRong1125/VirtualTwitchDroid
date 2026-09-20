package com.example.virtualtwitchdroid.feature.voice.assets

import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Every voice model must be bundled in `feature/voice/src/main/assets/voice/` at the exact size and sha256
 * pinned in [VoiceModels], so a fresh `git clone` builds an APK that runs the voice with no download and no
 * `adb push` (the stated goal). `ModelStore` copies these assets into `filesDir` on first launch. A missing
 * or wrong-hash file is a failure, not a skip — there is no CI here to notice a silently skipped gate.
 *
 * These files are large (the encoder alone is ~155 MB, over GitHub's 100 MB/file limit), so the repo must
 * track them with Git LFS; this test does not care how, only that the working tree has the right bytes.
 */
class BundledVoiceModelsTest {

    private fun repoRoot(): File {
        var dir: File = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile ?: break
        return dir
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun everyModelIsBundledInAssets_atThePinnedSizeAndHash() {
        val assetsDir = File(repoRoot(), "feature/voice/src/main/assets/voice")
        for (model in VoiceModels.ALL) {
            val file = File(assetsDir, model.name)
            assertTrue(
                file.isFile,
                "${model.name} must be at feature/voice/src/main/assets/voice/ (Git LFS not fetched?)",
            )
            assertEquals(model.bytes, file.length(), "${model.name}: wrong size (Git LFS not fetched?)")
            assertEquals(model.sha256, sha256(file), "${model.name}: sha256 does not match the pinned value")
        }
    }
}
