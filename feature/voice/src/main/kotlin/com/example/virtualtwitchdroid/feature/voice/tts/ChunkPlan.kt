package com.example.virtualtwitchdroid.feature.voice.tts

/**
 * Splits an utterance's accent phrases into synthesis chunks so playback can start before the whole
 * sentence is synthesised — **only when that cannot stutter**. Synthesis on a phone runs near real time
 * (RTF ≈ 1) plus a fixed per-call cost, so a chunk that is still being synthesised when the previous one
 * finishes playing would leave dead air mid-sentence. The plan is computed from what is known *before*
 * synthesis: each phrase's duration (from the `AudioQuery` moras) and this device's measured cost model
 * `synthesis(d) ≈ overhead + rtf · d` (Pixel 8a: ~0.7 s + 1.0–1.3 · d; a 0.9 s chunk measured 1.4–2.2 s).
 *
 * Rules: a chunk may only end where [ClauseBoundary] allows (an existing pause, or a clause-final particle
 * that gets a synthetic 、 — splitting inside a breath group would butt-join two vocoded waveforms); and both
 * the chunk and what follows it must be at least [MIN_CHUNK_S] (tiny chunks are all overhead). A chunk is then
 * closed when either:
 * - **gap-free** — the *remaining* audio, synthesised as one piece, finishes before the audio scheduled so far
 *   has been played (`overhead + rtf·SAFETY·D_rest ≤ D_scheduled`); or
 * - **fast start** — it is the *first* chunk and has reached [FIRST_CHUNK_TARGET_S]. This starts audio sooner on
 *   a long line even when synthesising the whole rest in one piece would not keep up: the later boundaries below
 *   still subdivide the rest gap-free, and any residual gap lands on a clause boundary (a natural breath, where
 *   the mouth is closed anyway). Only the first chunk gets this, so there is at most one such gap.
 *
 * The fast-start plan is **only used if its worst predicted gap stays within [MAX_FAST_START_GAP_S]** (simulated
 * from the same cost model). Otherwise the whole rest would be a single late chunk — several seconds of dead air
 * mid-sentence, not a breath, which happens on the first utterance of a process while [RtfEstimator] is still at
 * its pessimistic initial value — so the plan falls back to the strictly gap-free one (whole, on such a device).
 * Later utterances, once the estimate converges, get the fast start. Anything that fails every rule is synthesised
 * whole. Pure, unit-tested.
 */
object ChunkPlan {
    /** Multiplier on the measured RTF: the estimate is noisy and playback must never catch up with synthesis. */
    const val SAFETY = 1.15f

    /** Fixed cost of one `synthesis` call regardless of length (measured floor on the Pixel 8a). */
    const val OVERHEAD_S = 0.7f

    /** Neither a closed chunk nor the tail after it may be shorter than this (seconds of audio). */
    const val MIN_CHUNK_S = 1.0f

    /**
     * Close the first chunk at the earliest clause boundary once it reaches this, trading one possible
     * breath-length gap for a much earlier first audio on long lines. Above [MIN_CHUNK_S] so short sentences
     * (which have no early boundary this long) are still synthesised whole.
     */
    const val FIRST_CHUNK_TARGET_S = 1.5f

    /** The fast start is used only if its worst predicted mid-sentence gap stays within this (a breath). */
    const val MAX_FAST_START_GAP_S = 1.0f

    /**
     * @param phraseSeconds duration of each accent phrase's audio (moras + its pause), already speed-scaled.
     * @param pauseAfter whether a chunk may end after phrase `i` (pause mora or clause boundary, see [ClauseBoundary]).
     * @param rtf synthesis seconds per second of audio on this device, excluding the per-call overhead (≥ 0).
     */
    fun plan(
        phraseSeconds: List<Float>,
        pauseAfter: List<Boolean>,
        rtf: Float,
        overheadSeconds: Float = OVERHEAD_S,
        firstChunkTargetSeconds: Float = FIRST_CHUNK_TARGET_S,
    ): List<IntRange> {
        val n = phraseSeconds.size
        if (n == 0) return emptyList()
        require(pauseAfter.size == n) { "pauseAfter must match phrases" }
        val r = maxOf(rtf, 0f) * SAFETY
        val fast = split(phraseSeconds, pauseAfter, r, overheadSeconds, firstChunkTargetSeconds, fastStart = true)
        // The fast start only pays off when the gap it risks is at most a breath (converged RTF). When the model
        // predicts worse (a pessimistic first utterance with a long tail), fall back to the strictly gap-free plan.
        if (fast.size <= 1 || worstGapSeconds(fast, phraseSeconds, r, overheadSeconds) <= MAX_FAST_START_GAP_S) {
            return fast
        }
        return split(phraseSeconds, pauseAfter, r, overheadSeconds, firstChunkTargetSeconds, fastStart = false)
    }

    private fun split(
        phraseSeconds: List<Float>,
        pauseAfter: List<Boolean>,
        r: Float,
        overheadSeconds: Float,
        firstChunkTargetSeconds: Float,
        fastStart: Boolean,
    ): List<IntRange> {
        val n = phraseSeconds.size
        val total = phraseSeconds.sum()
        val out = ArrayList<IntRange>()
        var start = 0
        var scheduled = 0f // audio already assigned to closed chunks
        for (i in 0 until n - 1) {
            if (!pauseAfter[i]) continue
            val chunkSeconds = phraseSeconds.subList(start, i + 1).sum()
            val rest = total - scheduled - chunkSeconds
            val wouldSchedule = scheduled + chunkSeconds
            if (chunkSeconds < MIN_CHUNK_S || rest < MIN_CHUNK_S) continue
            // Gap-free: the whole tail is synthesised before what is scheduled so far has finished playing.
            val gapFree = overheadSeconds + r * rest <= wouldSchedule
            // Fast start: get the first chunk out early; the later boundaries catch the pipeline up (see the doc).
            val fast = fastStart && out.isEmpty() && chunkSeconds >= firstChunkTargetSeconds
            if (gapFree || fast) {
                out += start..i
                start = i + 1
                scheduled = wouldSchedule
            }
        }
        out += start until n
        return out
    }

    /**
     * The longest silence [plan] would leave mid-sentence: synthesis is sequential (each chunk costs
     * `overhead + r·duration`), playback is continuous from when the first chunk is ready, and a chunk not yet
     * synthesised when its turn comes stalls playback until it is.
     */
    private fun worstGapSeconds(
        ranges: List<IntRange>,
        phraseSeconds: List<Float>,
        r: Float,
        overheadSeconds: Float,
    ): Float {
        var synthDone = 0f // when each chunk finishes synthesising (sequential)
        var playEnd = 0f // when playback of the audio so far ends
        var worst = 0f
        for ((k, range) in ranges.withIndex()) {
            val duration = range.sumOf { phraseSeconds[it].toDouble() }.toFloat()
            synthDone += overheadSeconds + r * duration
            if (k == 0) {
                playEnd = synthDone + duration // playback starts when the first chunk is ready
            } else {
                worst = maxOf(worst, synthDone - playEnd) // audio ready later than needed → that much silence
                playEnd = maxOf(synthDone, playEnd) + duration
            }
        }
        return worst
    }
}
