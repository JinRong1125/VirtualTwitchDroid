package com.example.virtualtwitchdroid.feature.voice.asr

/**
 * Half-duplex gate: recognised speech is only accepted while Zundamon is **not** audible locally, so the
 * speaker's playback can never be re-recognised. With the local monitor off (the broadcast-only case)
 * nothing is ever gated. A segment is dropped if any part of it — from its start to its end — overlaps the
 * monitor's playback or the [TAIL_MS] after it (room reverberation). Written on the TTS thread, read on the
 * ASR thread.
 */
class ListenPolicy(private val tailMs: Long = TAIL_MS) {
    @Volatile private var speakingSinceMs = Long.MAX_VALUE

    @Volatile private var speakingUntilMs = Long.MIN_VALUE

    /** Forget any playback in progress (the session stopped; nothing is audible any more). */
    fun reset() {
        speakingSinceMs = Long.MAX_VALUE
        speakingUntilMs = Long.MIN_VALUE
    }

    /** Report the local monitor's state at [nowMs]. */
    fun monitorSpeaking(speaking: Boolean, nowMs: Long) {
        if (speaking) {
            if (speakingSinceMs == Long.MAX_VALUE) speakingSinceMs = nowMs
            speakingUntilMs = Long.MAX_VALUE
        } else if (speakingUntilMs == Long.MAX_VALUE) {
            speakingUntilMs = nowMs + tailMs
            speakingSinceMs = Long.MAX_VALUE
        }
    }

    /** Whether a recognised segment spanning [startMs]..[endMs] may be used. */
    fun accepts(startMs: Long, endMs: Long): Boolean {
        val until = speakingUntilMs
        if (until == Long.MAX_VALUE) return endMs < speakingSinceMs // still speaking: only segments that ended before
        return startMs > until || endMs < lastSpeakingSince(until)
    }

    // A segment entirely before the last playback is fine; we only know the playback's end here, so treat
    // everything that ended before the tail began as older than the playback (conservative for ASR latency).
    private fun lastSpeakingSince(until: Long) = until - tailMs - MAX_UTTERANCE_MS

    companion object {
        const val TAIL_MS = 300L

        /** Longest playback we assume when deciding a segment predates it. */
        const val MAX_UTTERANCE_MS = 30_000L
    }
}
