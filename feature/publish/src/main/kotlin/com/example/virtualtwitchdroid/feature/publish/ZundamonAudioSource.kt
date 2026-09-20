package com.example.virtualtwitchdroid.feature.publish

import android.os.SystemClock
import com.example.virtualtwitchdroid.core.common.media.PcmSink
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.BufferAudioSource

/**
 * RootEncoder audio source for Zundamon-voice mode: the broadcast's audio is whatever the voice pipeline
 * writes into [sink], never the microphone. It is a thin adapter over RootEncoder's own
 * `BufferAudioSource` — a paced ring buffer that zero-fills while nothing is queued and stamps frames so
 * that a byte written at uptime *W* is encoded with timestamp ≈ *W* — so silence, timing and the encoder
 * hand-off stay RootEncoder's problem. RootEncoder calls [start] only when streaming/recording begins
 * ([PcmSink.consuming] is false off-air: the voice then plays through the local monitor instead).
 *
 * [PcmSink.queuedMs] is tracked here (the ring's occupancy is private to RootEncoder): bytes written minus
 * bytes the ring has drained at its constant rate since [start].
 */
internal class ZundamonAudioSource(private val uptimeMs: () -> Long = SystemClock::uptimeMillis) : AudioSource() {
    private val buffer = BufferAudioSource(bufferCapacityMs = CAPACITY_MS, latencyMs = LATENCY_MS)
    private var startedAtMs = 0L
    private var writtenBytes = 0L

    /** The voice pipeline's view of this source (format follows the encoder's `init`). */
    val sink: PcmSink = object : PcmSink {
        override val sampleRate: Int get() = this@ZundamonAudioSource.sampleRate
        override val stereo: Boolean get() = this@ZundamonAudioSource.isStereo
        override val latencyMs: Int get() = LATENCY_MS
        override val consuming: Boolean get() = buffer.isRunning()
        override val queuedMs: Int get() = queuedMs()

        /** Bytes accepted into the ring (fewer than [size] when it is full — the caller waits and retries). */
        override fun write(pcm: ByteArray, offset: Int, size: Int): Int {
            if (!buffer.isRunning()) return size // nobody drains off-air: report accepted, nothing queued
            val accepted = buffer.setBuffer(pcm, offset, size)
            synchronized(this@ZundamonAudioSource) { writtenBytes += accepted }
            return accepted
        }
    }

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean =
        buffer.init(sampleRate, isStereo, echoCanceler, noiseSuppressor)

    override fun start(getMicrophoneData: GetMicrophoneData) {
        synchronized(this) {
            startedAtMs = uptimeMs()
            writtenBytes = 0L
        }
        buffer.start(getMicrophoneData)
    }

    override fun stop() = buffer.stop()

    override fun isRunning(): Boolean = buffer.isRunning()

    override fun release() = buffer.release()

    /** Written audio not yet drained: the ring plays at `bytesPerSecond` from `start + latency`, zero-filling gaps. */
    private fun queuedMs(): Int = synchronized(this) {
        if (!buffer.isRunning() || sampleRate <= 0) return 0
        val bytesPerMs = sampleRate * (if (isStereo) 2 else 1) * 2 / 1_000f
        val drainedMs = (uptimeMs() - startedAtMs - LATENCY_MS).coerceAtLeast(0L)
        val writtenMs = writtenBytes / bytesPerMs
        (writtenMs - drainedMs).toInt().coerceAtLeast(0)
    }

    companion object {
        /** Ring capacity: enough for one synthesis chunk to be queued ahead of the encoder. */
        const val CAPACITY_MS = 3_000

        /** RootEncoder's drain margin: the ring starts draining this long after `start`. */
        const val LATENCY_MS = 100
    }
}
