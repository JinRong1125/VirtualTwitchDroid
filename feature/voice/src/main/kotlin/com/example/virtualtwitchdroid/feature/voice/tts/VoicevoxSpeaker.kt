package com.example.virtualtwitchdroid.feature.voice.tts

import android.os.SystemClock
import android.util.Log
import com.example.virtualtwitchdroid.feature.voice.assets.VoiceModels
import com.example.virtualtwitchdroid.feature.voice.assets.VoicePaths
import com.example.virtualtwitchdroid.feature.voice.audio.Pcm16
import com.example.virtualtwitchdroid.feature.voice.audio.WavPcm
import com.example.virtualtwitchdroid.feature.voice.lipsync.MoraTiming
import com.example.virtualtwitchdroid.feature.voice.lipsync.PhraseTiming
import com.example.virtualtwitchdroid.feature.voice.lipsync.UtteranceTiming
import jp.hiroshiba.voicevoxcore.AccelerationMode
import jp.hiroshiba.voicevoxcore.AccentPhrase
import jp.hiroshiba.voicevoxcore.AudioQuery
import jp.hiroshiba.voicevoxcore.Mora
import jp.hiroshiba.voicevoxcore.blocking.Onnxruntime
import jp.hiroshiba.voicevoxcore.blocking.OpenJtalk
import jp.hiroshiba.voicevoxcore.blocking.Synthesizer
import jp.hiroshiba.voicevoxcore.blocking.VoiceModelFile

/** One synthesised chunk of an utterance: the audio and the timing its lips follow. */
class Synthesized(val pcm: Pcm16, val timing: UtteranceTiming, val text: String, val synthesisSeconds: Float = 0f)

/**
 * The text-to-speech stage behind an interface so the controller can be tested with a fake. An utterance
 * is emitted as one or more chunks in playback order; the caller schedules each as it arrives.
 */
interface Speaker {
    /** [rtf] is the device's measured synthesis real-time factor, used to decide whether chunking can be gap-free. */
    fun synthesize(text: String, speedScale: Float, rtf: Float, emit: (Synthesized) -> Unit)

    fun release()
}

/**
 * VOICEVOX CORE with the ずんだもん ノーマル style: loads VOICEVOX's ONNX Runtime from `jniLibs`, the Open
 * JTalk dictionary and `0.vvm`, then `createAudioQuery` once per utterance and `synthesis` **per chunk**
 * when [ChunkPlan] finds a gap-free split at a pause for this device's measured RTF (otherwise whole), so
 * audio can start before the whole sentence is synthesised. Inner chunks carry no pre/post phoneme pad. The `AudioQuery`'s moras are converted to the engine-agnostic
 * [UtteranceTiming] for lip-sync. Not thread-safe — the controller runs it on one TTS thread.
 * `cpuNumThreads` is 4: measured on a Pixel 8a, 2 threads gave RTF 1.6–1.75, 4 gave 1.0–1.2, 6 gave 1.5–1.65
 * (the extra threads spill onto the little cores).
 */
class VoicevoxSpeaker(
    paths: VoicePaths,
    threads: Int = TTS_THREADS,
    private val styleId: Int = VoiceModels.ZUNDAMON_NORMAL_STYLE,
) : Speaker {
    private val synthesizer: Synthesizer

    // Held for the synthesizer's lifetime and never closed on purpose: VOICEVOX's VoiceModelFile.finalize calls
    // the native drop again on an already-closed handle, which throws "Null pointer in rust value" from the
    // finalizer thread (seen on the Pixel 8a). Keeping it — one fd on a process-lifetime singleton — lets the
    // native model drop exactly once, at GC, on a valid handle. Do NOT wrap the load in `.use { }`.
    private val voiceModel: VoiceModelFile

    init {
        val started = SystemClock.uptimeMillis()
        // The AAR does not carry the runtime; System.loadLibrary makes the jniLibs copy resolvable by soname.
        System.loadLibrary(ORT_LIBRARY)
        val onnxruntime = Onnxruntime.get().orElseGet { Onnxruntime.loadOnce().filename(ORT_SONAME).perform() }
        val openJtalk = OpenJtalk(paths.dictDir)
        // VOICEVOX's runtime knows only CUDA and DirectML as GPU devices; its Android build reports neither,
        // so this resolves to the CPU on every phone today — asked at runtime rather than assumed, so a
        // future runtime with a mobile GPU path is picked up without a code change.
        val devices = onnxruntime.supportedDevices()
        val mode = accelerationModeFor(gpuCuda = devices.cuda, gpuDml = devices.dml)
        synthesizer = Synthesizer.builder(onnxruntime, openJtalk)
            .accelerationMode(mode)
            .cpuNumThreads(threads)
            .build()
        voiceModel = VoiceModelFile(paths.vvm)
        synthesizer.loadVoiceModel(voiceModel).perform()
        val styles = synthesizer.metas().flatMap { c -> c.styles.map { "${c.name}/${it.name}=${it.id}" } }
        Log.i(
            TAG,
            "VOICEVOX ready in ${SystemClock.uptimeMillis() - started} ms, style $styleId, $threads threads, $mode " +
                "(runtime devices: cpu=${devices.cpu} cuda=${devices.cuda} dml=${devices.dml}); loaded: $styles",
        )
    }

    override fun synthesize(text: String, speedScale: Float, rtf: Float, emit: (Synthesized) -> Unit) {
        val t0 = SystemClock.uptimeMillis()
        val query = synthesizer.createAudioQuery(text, styleId)
        query.speedScale = speedScale.toDouble()
        val t1 = SystemClock.uptimeMillis()
        val speed = if (speedScale > 0f) speedScale else 1f
        val phraseSeconds = query.accentPhrases.map { p ->
            (
                p.moras.sumOf {
                    (it.consonantLength ?: 0.0) + it.vowelLength
                } + (p.pauseMora?.vowelLength ?: 0.0)
                ).toFloat() /
                speed
        }
        // Split points: an existing pause, or a clause-final particle (the recogniser gives no 、).
        val splittable = query.accentPhrases.map { p ->
            ClauseBoundary.isSplittable(p.moras.map { it.text }, hasPause = p.pauseMora != null)
        }
        val ranges = ChunkPlan.plan(phraseSeconds, splittable, rtf).ifEmpty {
            listOf(0 until maxOf(query.accentPhrases.size, 0))
        }
        var totalAudio = 0f
        for ((i, range) in ranges.withIndex()) {
            val whole = ranges.size == 1
            val chunk = if (whole) query else subQuery(query, range, first = i == 0, last = i == ranges.lastIndex)
            val tc = SystemClock.uptimeMillis()
            val pcm = WavPcm.decode(synthesizer.synthesis(chunk, styleId).perform())
            val now = SystemClock.uptimeMillis()
            totalAudio += pcm.durationSeconds
            val firstNote = when (i) {
                0 -> " — first audio ${now - t0} ms after the text arrived (query ${t1 - t0} ms, plan rtf %.2f)".format(
                    rtf,
                )
                else -> ""
            }
            Log.i(
                TAG,
                "chunk ${i + 1}/${ranges.size}: %.2fs audio in %d ms (RTF %.2f)%s"
                    .format(pcm.durationSeconds, now - tc, (now - tc) / 1000f / pcm.durationSeconds, firstNote),
            )
            emit(Synthesized(pcm, timingOf(chunk), text, synthesisSeconds = (now - tc) / 1000f))
        }
        val total = SystemClock.uptimeMillis() - t0
        Log.i(
            TAG,
            "synthesised %.2fs of audio in %d ms total, speed %.2f: %s".format(totalAudio, total, speedScale, text),
        )
    }

    /**
     * The accent phrases in [range] as their own query: same voice parameters, pads only at the utterance's
     * ends, and a short synthetic 、 pause on the chunk's last phrase when it had none (a clause-final cut),
     * so consecutive chunks meet in silence.
     */
    private fun subQuery(whole: AudioQuery, range: IntRange, first: Boolean, last: Boolean): AudioQuery {
        val phrases = whole.accentPhrases.subList(range.first, range.last + 1).mapIndexed { i, p ->
            val cutHere = i == range.last - range.first && !last && p.pauseMora == null
            if (cutHere) withSyntheticPause(p) else p
        }
        return AudioQuery.fromAccentPhrases(phrases).also { sub ->
            sub.speedScale = whole.speedScale
            sub.pitchScale = whole.pitchScale
            sub.intonationScale = whole.intonationScale
            sub.volumeScale = whole.volumeScale
            sub.outputSamplingRate = whole.outputSamplingRate
            sub.outputStereo = whole.outputStereo
            sub.prePhonemeLength = if (first) whole.prePhonemeLength else 0.0
            sub.postPhonemeLength = if (last) whole.postPhonemeLength else 0.0
        }
    }

    private fun withSyntheticPause(p: AccentPhrase): AccentPhrase = AccentPhrase().also { copy ->
        copy.moras = p.moras
        copy.accent = p.accent
        copy.isInterrogative = p.isInterrogative
        copy.pauseMora = Mora().also { m ->
            m.text = "、"
            m.vowel = "pau"
            m.vowelLength = ClauseBoundary.SYNTHETIC_PAUSE_S.toDouble()
            m.pitch = 0.0
        }
    }

    override fun release() = Unit // the Rust objects are freed by their finalizers; nothing else to close

    companion object {
        const val TTS_THREADS = 4

        /** GPU only when the loaded runtime reports a GPU device it can drive; otherwise the CPU. */
        fun accelerationModeFor(gpuCuda: Boolean, gpuDml: Boolean): AccelerationMode =
            if (gpuCuda || gpuDml) AccelerationMode.GPU else AccelerationMode.CPU
        private const val ORT_LIBRARY = "voicevox_onnxruntime"
        private const val ORT_SONAME = "libvoicevox_onnxruntime.so"
        private const val TAG = "VoicevoxSpeaker"

        /** The lip-sync timing of an [AudioQuery] (all lengths in seconds before `speedScale`). */
        fun timingOf(query: AudioQuery): UtteranceTiming = UtteranceTiming(
            phrases = query.accentPhrases.map { phrase ->
                PhraseTiming(
                    moras = phrase.moras.map { m ->
                        MoraTiming(m.consonantLength?.toFloat(), m.vowel, m.vowelLength.toFloat())
                    },
                    pauseSeconds = phrase.pauseMora?.vowelLength?.toFloat(),
                )
            },
            prePhonemeSeconds = query.prePhonemeLength.toFloat(),
            postPhonemeSeconds = query.postPhonemeLength.toFloat(),
            speedScale = query.speedScale.toFloat(),
        )
    }
}
