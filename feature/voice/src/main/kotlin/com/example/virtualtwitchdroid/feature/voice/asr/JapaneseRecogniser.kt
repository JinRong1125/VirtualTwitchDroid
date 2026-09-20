package com.example.virtualtwitchdroid.feature.voice.asr

import android.os.SystemClock
import android.util.Log
import com.example.virtualtwitchdroid.feature.voice.assets.VoicePaths
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.Locale

/** One recognised speech segment. */
data class Recognised(val text: String, val audioSeconds: Float, val decodeMs: Long)

/** The speech-to-text stage behind an interface so the controller can be tested with a fake. */
interface Recogniser {
    /** Feed one microphone chunk; returns the segments that completed (usually none). */
    fun feed(samples: FloatArray): List<Recognised>

    fun reset()

    fun release()
}

/**
 * Silero VAD + the ReazonSpeech k2 v2 Zipformer (int8) through sherpa-onnx's Kotlin API: microphone chunks
 * go into the VAD; each finished speech segment is decoded offline with greedy search. Not thread-safe —
 * the controller drives it from one ASR thread. Native thread counts are set explicitly (the Zipformer
 * pool would otherwise take every core from face tracking, Filament and the encoder).
 *
 * [provider] is the ONNX Runtime execution provider for the Zipformer (`"cpu"` or `"nnapi"`, the only
 * Android accelerator the sherpa-onnx build wires up; the VAD is 2 MB and stays on the CPU). **The vendored sherpa-onnx
 * 1.13.8 AAR is compiled with `__ANDROID_API__ = 21`, so its NNAPI branch is compiled out**: asking for
 * `"nnapi"` logs "Android NNAPI requires API level >= 27. Current API level 21 Fallback to cpu!" and runs
 * on the CPU (verified on a Pixel 8a, Android 15, which does ship an NNAPI driver, `google-edgetpu`).
 * Reaching the accelerator would need a sherpa-onnx JNI rebuilt for API ≥ 27 — and the Zipformer is not
 * the bottleneck (RTF 0.05–0.09 on 2 CPU threads). If a non-CPU provider is requested and the recogniser
 * cannot be created with it, it is rebuilt on the CPU (sherpa itself already downgrades an unavailable
 * provider, so this only covers a build that rejects the configuration outright).
 */
class JapaneseRecogniser(paths: VoicePaths, numThreads: Int = ASR_THREADS, provider: String = ASR_PROVIDER) :
    Recogniser {
    private val vad = Vad(
        assetManager = null,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = paths.vad,
                threshold = VAD_THRESHOLD,
                minSilenceDuration = MIN_SILENCE_S,
                minSpeechDuration = MIN_SPEECH_S,
                windowSize = MicCapture.CHUNK_SAMPLES,
                maxSpeechDuration = MAX_SPEECH_S,
            ),
            sampleRate = MicCapture.SAMPLE_RATE,
            numThreads = 1,
            provider = CPU_PROVIDER,
        ),
    )

    /** The provider actually in use (the requested one, or the CPU after a failed initialisation). */
    internal val provider: String

    private val recognizer: OfflineRecognizer

    init {
        val started = SystemClock.uptimeMillis()
        var used = provider
        recognizer = if (provider == CPU_PROVIDER) {
            buildRecognizer(paths, numThreads, CPU_PROVIDER)
        } else {
            try {
                buildRecognizer(paths, numThreads, provider)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Zipformer could not be created on '$provider'; rebuilding on the CPU", e)
                used = CPU_PROVIDER
                buildRecognizer(paths, numThreads, CPU_PROVIDER)
            }
        }
        this.provider = used
        Log.i(
            TAG,
            "ReazonSpeech Zipformer loaded on '${this.provider}' ($numThreads threads) in " +
                "${SystemClock.uptimeMillis() - started} ms; VAD silence ${MIN_SILENCE_S}s, max speech ${MAX_SPEECH_S}s",
        )
    }

    private fun buildRecognizer(paths: VoicePaths, numThreads: Int, provider: String) = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = MicCapture.SAMPLE_RATE, featureDim = FEATURE_DIM, dither = 0f),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = paths.encoder,
                    decoder = paths.decoder,
                    joiner = paths.joiner,
                ),
                tokens = paths.tokens,
                numThreads = numThreads,
                provider = provider,
                modelType = "transducer",
            ),
            // Greedy: measured on the Pixel 8a, `modified_beam_search` + hotwords (ずんだもん, ユーチューブ…) tripled the
            // decode time (RTF 0.05 → 0.17) and still lost 「ずんだもん」 at an utterance start while breaking a tongue
            // twister; the stream's proper nouns are fixed deterministically in TextNormaliser instead.
            decodingMethod = "greedy_search",
        ),
    )

    override fun feed(samples: FloatArray): List<Recognised> {
        vad.acceptWaveform(samples)
        if (vad.empty()) return emptyList()
        val out = ArrayList<Recognised>()
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            val started = SystemClock.uptimeMillis()
            val stream = recognizer.createStream()
            val text = try {
                // Leading/trailing silence around the VAD's tight cut — without it the Zipformer drops the first word.
                stream.acceptWaveform(
                    SegmentPadding.pad(segment.samples, MicCapture.SAMPLE_RATE),
                    MicCapture.SAMPLE_RATE,
                )
                recognizer.decode(stream)
                recognizer.getResult(stream).text
            } finally {
                stream.release()
            }
            val seconds = segment.samples.size / MicCapture.SAMPLE_RATE.toFloat()
            val ms = SystemClock.uptimeMillis() - started
            // The VAD closes a segment MIN_SILENCE_S after speech ends, so text arrives ≈ that + decode later.
            Log.i(
                TAG,
                "segment %.2fs → %d ms (RTF %.2f; text %d ms after end of speech): %s"
                    .format(
                        Locale.ROOT,
                        seconds,
                        ms,
                        ms / MILLIS_PER_SECOND / seconds,
                        ms + (MIN_SILENCE_S * MILLIS_PER_SECOND).toLong(),
                        text,
                    ),
            )
            out += Recognised(text, seconds, ms)
        }
        return out
    }

    override fun reset() = vad.reset()

    override fun release() {
        vad.release()
        recognizer.release()
    }

    companion object {
        const val ASR_THREADS = 2
        const val CPU_PROVIDER = "cpu"

        /** Execution provider for the Zipformer — the CPU until the sherpa-onnx build can reach NNAPI (class doc). */
        const val ASR_PROVIDER = CPU_PROVIDER
        const val FEATURE_DIM = 80
        const val VAD_THRESHOLD = 0.5f

        /** Trailing silence that ends an utterance; shorter → more fragments, longer → more lag. */
        const val MIN_SILENCE_S = 0.3f
        const val MIN_SPEECH_S = 0.25f

        /** Soft cap (sherpa raises the threshold past it); the Zipformer is rated for ~30 s clips. */
        const val MAX_SPEECH_S = 8f
        private const val TAG = "JapaneseRecogniser"
        private const val MILLIS_PER_SECOND = 1_000f
    }
}
