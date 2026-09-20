package com.example.virtualtwitchdroid.feature.voice.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/** The microphone behind an interface so the controller can be tested with a fake. */
interface MicSource {
    fun start()

    fun stop()
}

/**
 * The voice pipeline's microphone: 16 kHz mono 16-bit `AudioRecord` on the `VOICE_RECOGNITION` source
 * (no AEC/AGC tuning — that degrades ASR), read on its own thread in [CHUNK_SAMPLES]-sample chunks (Silero
 * VAD's window) and handed to [onChunk] as `-1..1` floats. The single microphone owner in Zundamon-voice
 * mode; RootEncoder's `MicrophoneSource` is swapped out while this runs. Requires RECORD_AUDIO.
 */
class MicCapture(private val onChunk: (FloatArray) -> Unit) : MicSource {
    private var thread: Thread? = null

    @Volatile private var running = false

    @SuppressLint("MissingPermission") // the publisher/avatar screens gate on RECORD_AUDIO before starting
    override fun start() {
        if (running) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK_SAMPLES * 2 * BUFFER_CHUNKS),
        )
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
            record.startRecording()
        } catch (e: RuntimeException) {
            record.release() // never leak the recorder on a failed open
            throw e
        }
        running = true
        thread = Thread({
            val shorts = ShortArray(CHUNK_SAMPLES)
            val floats = FloatArray(CHUNK_SAMPLES)
            try {
                while (running) {
                    val n = record.read(shorts, 0, CHUNK_SAMPLES)
                    if (n <= 0) continue
                    for (i in 0 until n) floats[i] = shorts[i] / SHORT_SCALE
                    onChunk(if (n == CHUNK_SAMPLES) floats.copyOf() else floats.copyOf(n))
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                Log.i(TAG, "microphone released")
            }
        }, "zundamon-mic").also { it.start() }
        Log.i(TAG, "microphone opened (${SAMPLE_RATE} Hz mono, VOICE_RECOGNITION)")
    }

    override fun stop() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 512
        private const val BUFFER_CHUNKS = 8
        private const val SHORT_SCALE = 32_768f
        private const val JOIN_TIMEOUT_MS = 1_000L
        private const val TAG = "MicCapture"
    }
}
