package com.example.virtualtwitchdroid.feature.voice.assets

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.security.MessageDigest

/** Absolute paths of every model file once [ModelStore.ensure] has succeeded. */
class VoicePaths(
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
    val vad: String,
    val vvm: String,
    val dictDir: String,
)

/**
 * Owns `filesDir/voice/`: the model files ([VoiceModels]) are **bundled in the APK** under `assets/voice/`,
 * so a fresh clone builds and runs with no download and no `adb push`. On first launch this copies each one
 * out of assets into `filesDir/voice/` (a `.part` file renamed only after its size + sha256 match the pinned
 * value; verified once, then remembered in a `.sha256.ok` marker) and unpacks the dictionary archive. The
 * engines need real filesystem paths — VOICEVOX's `VoiceModelFile`/`OpenJtalk` cannot read an AssetManager —
 * which is why the files are materialised here rather than read in place. Blocking I/O — call from a worker
 * thread. (`scripts/voice-models.sh` remains only as a way to refresh the files on a device without a rebuild.)
 */
class ModelStore(context: Context) {
    val dir: File = File(context.filesDir, VoiceModels.DIR).apply { mkdirs() }
    private val assets: AssetManager = context.assets

    /**
     * Makes every model available (copying what is missing out of the bundled assets, reporting each file's
     * progress `0..1` through [onProgress]) and returns the paths; throws when an asset is absent or a hash
     * mismatches.
     */
    fun ensure(onProgress: (ModelFile, Float) -> Unit = { _, _ -> }): VoicePaths {
        for (model in VoiceModels.ALL) {
            if (!verified(model)) copyFromAssets(model, onProgress)
            model.unpackDir?.let { unpackIfNeeded(model, it) }
        }
        return VoicePaths(
            encoder = file(VoiceModels.ASR_ENCODER).path,
            decoder = file(VoiceModels.ASR_DECODER).path,
            joiner = file(VoiceModels.ASR_JOINER).path,
            tokens = file(VoiceModels.ASR_TOKENS).path,
            vad = file(VoiceModels.VAD).path,
            vvm = file(VoiceModels.VOICE_VVM).path,
            dictDir = File(dir, VoiceModels.DICT_DIR).path,
        )
    }

    fun file(model: ModelFile): File = File(dir, model.name)

    private fun marker(model: ModelFile) = File(dir, model.name + ".sha256.ok")

    private fun verified(model: ModelFile): Boolean {
        val f = file(model)
        if (!f.isFile || f.length() != model.bytes) return false
        if (marker(model).isFile) return true
        val ok = sha256(f) == model.sha256
        if (ok) {
            marker(
                model,
            ).writeText(model.sha256)
        } else {
            Log.w(TAG, "${model.name}: sha256 mismatch, will re-download")
        }
        return ok
    }

    private fun copyFromAssets(model: ModelFile, onProgress: (ModelFile, Float) -> Unit) {
        val part = File(dir, model.name + ".part")
        Log.i(TAG, "materialising ${model.name} (${model.bytes / MEGABYTE} MB) from assets")
        assets.open(ASSET_DIR + "/" + model.name).use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgress(model, (total.toFloat() / model.bytes).coerceIn(0f, 1f))
                }
            }
        }
        check(part.length() == model.bytes) { "${model.name}: got ${part.length()} bytes, expected ${model.bytes}" }
        val hash = sha256(part)
        check(hash == model.sha256) { "${model.name}: sha256 $hash does not match the pinned value" }
        val target = file(model)
        target.delete()
        check(part.renameTo(target)) { "could not move ${part.name} into place" }
        marker(model).writeText(model.sha256)
    }

    private fun unpackIfNeeded(model: ModelFile, dirName: String) {
        val target = File(dir, dirName)
        if (File(target, UNPACKED_MARKER).isFile) return
        Log.i(TAG, "unpacking ${model.name}")
        target.deleteRecursively()
        file(model).inputStream().use { TarGz.extract(it, dir) }
        check(target.isDirectory) { "${model.name} did not contain $dirName/" }
        File(target, UNPACKED_MARKER).writeText(model.sha256)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "ModelStore"

        /** Where the models are bundled in the APK: `feature/voice/src/main/assets/voice/`. */
        const val ASSET_DIR = "voice"
        const val UNPACKED_MARKER = ".unpacked.ok"
        const val BUFFER_BYTES = 256 * 1024
        const val MEGABYTE = 1_000_000
    }
}
