package com.example.virtualtwitchdroid.feature.voice.tts

/**
 * The backlog policy between recognition and synthesis (§6 of the plan): Zundamon speaks a little slower
 * than lively conversation, so continuous talking would enqueue faster than playback drains and the lag
 * would grow without bound. The queue keeps at most [MAX_PENDING] texts (the oldest is dropped beyond that),
 * and [speedScaleFor] raises VOICEVOX's `speedScale` when the audio backlog is long. Thread-safe: producers
 * (ASR thread, main) and the single TTS consumer share it.
 */
class UtteranceQueue(private val maxPending: Int = MAX_PENDING) {
    private val pending = ArrayDeque<String>()
    private val lock = Any()

    /** Number of texts dropped to keep the lag bounded (diagnostics). */
    var dropped = 0
        private set

    val size: Int get() = synchronized(lock) { pending.size }

    fun offer(text: String) = synchronized(lock) {
        while (pending.size >= maxPending) {
            pending.removeFirst()
            dropped++
        }
        pending.addLast(text)
    }

    fun poll(): String? = synchronized(lock) { pending.removeFirstOrNull() }

    fun clear() = synchronized(lock) { pending.clear() }

    companion object {
        const val MAX_PENDING = 2

        /** Backlog (seconds of audio still to play) above which speech is sped up, and the ceiling. */
        const val SPEED_UP_ABOVE_SECONDS = 4f
        const val MAX_BACKLOG_SECONDS = 8f
        const val NORMAL_SPEED = 1f
        const val FAST_SPEED = 1.3f

        /** `speedScale` for the next utterance given the current audio backlog: 1.0 up to 4 s, ramping to 1.3 at 8 s. */
        fun speedScaleFor(backlogSeconds: Float): Float {
            if (backlogSeconds <= SPEED_UP_ABOVE_SECONDS) return NORMAL_SPEED
            val ramp = MAX_BACKLOG_SECONDS - SPEED_UP_ABOVE_SECONDS
            val t = ((backlogSeconds - SPEED_UP_ABOVE_SECONDS) / ramp).coerceIn(0f, 1f)
            return NORMAL_SPEED + (FAST_SPEED - NORMAL_SPEED) * t
        }
    }
}
