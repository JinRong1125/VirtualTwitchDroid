package com.example.virtualtwitchdroid.core.common.media

import kotlinx.coroutines.flow.StateFlow

/** Where the voice pipeline is right now, for the UI and the health logs. */
enum class VoiceState {
    /** Not running (mode off, or models not ready). */
    IDLE,

    /** Models are being unpacked / loaded. */
    PREPARING,

    /** Microphone open, waiting for speech. */
    LISTENING,

    /** A speech segment is being recognised or synthesised. */
    THINKING,

    /** Zundamon audio is being produced. */
    SPEAKING,

    /** Something is wrong (see [SpeechStreamSource.error]); the pipeline stopped. */
    ERROR,
}

/**
 * A consumer of 16-bit little-endian PCM at a fixed [sampleRate] / channel layout — the broadcast's audio
 * source. [write] returns how many bytes it accepted (a bounded buffer may take fewer); the producer paces
 * itself on that. [latencyMs] is how long after a write the first of those bytes is heard, so lips can be
 * anchored to it.
 */
interface PcmSink {
    val sampleRate: Int
    val stereo: Boolean
    val latencyMs: Int

    /** True while the sink is actually draining audio (the broadcast/recording runs); false off-air. */
    val consuming: Boolean

    /** Milliseconds of already-written audio still waiting to be heard — the next write starts after it. */
    val queuedMs: Int

    fun write(pcm: ByteArray, offset: Int, size: Int): Int
}

/**
 * The Zundamon voice: listens to the streamer's Japanese, and speaks it back with VOICEVOX ずんだもん —
 * as PCM to an attached [PcmSink] (the broadcast) and/or the local speaker. Implemented by
 * `:feature:voice`, consumed by `:feature:publish` (audio source + toggle) and `:feature:avatar`
 * (the test field on the Avatar tab); features never depend on each other.
 *
 * All methods may be called from the main thread; the pipeline runs on its own threads.
 */
interface SpeechStreamSource {
    /** False when this device/build cannot run the voice (SDK < 26, or the model files are absent). */
    val supported: Boolean

    val state: StateFlow<VoiceState>

    /** The last recognised (and normalised) Japanese text, for the overlay. */
    val lastText: StateFlow<String?>

    /** Seconds of Zundamon audio still queued behind what is playing now (the streamer's lag). */
    val lagSeconds: StateFlow<Float>

    /** A human-readable reason while [state] is [VoiceState.ERROR]. */
    val error: StateFlow<String?>

    /** Start listening + speaking (loads the engines on first use). No-op when already running. */
    fun start()

    /** Stop everything: microphone closed, queued audio dropped, engines kept warm. */
    fun stop()

    /** Speak [text] directly (bypasses recognition) — the Avatar tab's test field. Starts the engines if needed. */
    fun say(text: String)

    /** Silence the output (and pause recognition) without stopping. */
    fun setMuted(muted: Boolean)

    val muted: StateFlow<Boolean>

    /** True while a broadcast sink is attached — the publisher owns the voice; rehearsal controls should yield. */
    val broadcasting: StateFlow<Boolean>

    /**
     * Route Zundamon's PCM to [sink] (the broadcast). Audio goes to the sink whenever it is consuming
     * (streaming/recording); otherwise — off-air, or with no sink — the local speaker plays it so the
     * streamer can rehearse (and recognition is gated while it does, see the half-duplex rule).
     */
    fun attachAudioSink(sink: PcmSink)

    fun detachAudioSink()
}
