package com.example.virtualtwitchdroid.feature.voice.tts

/**
 * Running estimate of this device's synthesis real-time factor — the *marginal* seconds of synthesis per
 * second of audio, after subtracting the fixed per-call [overheadSeconds] ([ChunkPlan.OVERHEAD_S]) — as an
 * exponential moving average of measurements. Starts pessimistic so the first utterance on an unknown phone is
 * planned conservatively — [ChunkPlan] then keeps it whole (or splits only within a bounded gap) until the
 * estimate converges. Single-writer (the TTS thread), read anywhere.
 */
class RtfEstimator(
    initial: Float = INITIAL_RTF,
    private val alpha: Float = ALPHA,
    private val overheadSeconds: Float = ChunkPlan.OVERHEAD_S,
) {
    @Volatile var estimate: Float = initial
        private set

    /** Fold one measurement in ([synthesisSeconds] spent producing [audioSeconds] of audio). */
    fun update(synthesisSeconds: Float, audioSeconds: Float) {
        if (audioSeconds <= 0f || synthesisSeconds < 0f) return
        val measured = ((synthesisSeconds - overheadSeconds) / audioSeconds).coerceAtLeast(MIN_RTF)
        estimate += (measured - estimate) * alpha
    }

    companion object {
        /** Slower than real time until proven otherwise. */
        const val INITIAL_RTF = 1.5f
        const val ALPHA = 0.4f

        /** A call faster than its overhead still costs something per second of audio. */
        const val MIN_RTF = 0.2f
    }
}
