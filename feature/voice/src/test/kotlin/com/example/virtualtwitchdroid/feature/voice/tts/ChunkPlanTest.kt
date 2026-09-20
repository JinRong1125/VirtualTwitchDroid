package com.example.virtualtwitchdroid.feature.voice.tts

import kotlin.test.assertEquals
import org.junit.Test

class ChunkPlanTest {
    // 「今日は雨でしたが、午後は晴れました。」-like: two phrases, a clause boundary after the first.
    private val twoPhrases = listOf(1.2f, 1.0f)
    private val boundaryAfterFirst = listOf(true, true)

    @Test
    fun emptyAndSingle() {
        assertEquals(emptyList(), ChunkPlan.plan(emptyList(), emptyList(), 1f))
        assertEquals(listOf(0..0), ChunkPlan.plan(listOf(1f), listOf(true), 0.1f))
    }

    @Test
    fun nearRealTimeSynthesis_keepsAShortSentenceWhole() {
        // 1.2 s first clause is below FIRST_CHUNK_TARGET_S (1.5), and the tail cannot keep up, so no split.
        assertEquals(listOf(0..1), ChunkPlan.plan(twoPhrases, boundaryAfterFirst, rtf = 1.1f))
    }

    @Test
    fun fastSynthesis_splitsAtTheBoundary() {
        // 0.7 + 0.3·1.15·1.0 = 1.045 s ≤ 1.2 s scheduled → gap-free → split.
        assertEquals(listOf(0..0, 1..1), ChunkPlan.plan(twoPhrases, boundaryAfterFirst, rtf = 0.3f))
    }

    @Test
    fun neverSplitsInsideABreathGroup() {
        // Fast device but no legal boundary until the very end → whole (fast start still needs a boundary).
        assertEquals(listOf(0..2), ChunkPlan.plan(listOf(1.8f, 1.8f, 1.8f), listOf(false, false, true), rtf = 0.2f))
    }

    @Test
    fun longLine_startsAudioEarly_thenLetsTheLaterBoundariesCatchUp() {
        // 5 phrases, boundaries everywhere, rtf 1.0. The first chunk closes at the earliest boundary past the
        // 1.5 s target (phrase 0 is 0.9 s < MIN, so 0..1 = 2.6 s), instead of the old gap-free-only 0..2 (3.6 s);
        // the tail then subdivides gap-free (2..2, then 3 is < MIN so the tail stays 3..4).
        val plan = ChunkPlan.plan(listOf(0.9f, 1.7f, 1.0f, 0.8f, 0.6f), List(5) { true }, rtf = 1.0f)
        assertEquals(listOf(0..1, 2..2, 3..4), plan)
    }

    @Test
    fun firstChunkTarget_startsAudioEarly_whenTheCatchUpGapStaysWithinABreath() {
        // 1.6 s first clause + a 1.5 s tail, rtf 1.0: fast start emits the first clause immediately; the tail is
        // ready ~0.8 s late (≤ MAX_FAST_START_GAP_S), a breath at the 、.
        assertEquals(listOf(0..0, 1..1), ChunkPlan.plan(listOf(1.6f, 1.5f), listOf(true, true), rtf = 1.0f))
        // A first clause just under the target is not split prematurely (no benefit, would add a breath).
        assertEquals(listOf(0..1), ChunkPlan.plan(listOf(1.4f, 2.0f), listOf(true, true), rtf = 1.0f))
    }

    @Test
    fun fallsBackToWhole_whenTheFastStartGapWouldExceedABreath() {
        // Pessimistic first-utterance RTF (1.5) with a long trailing breath group: fast start would leave several
        // seconds of dead air, not a breath, so the plan is the strictly gap-free one — here, whole.
        assertEquals(listOf(0..1), ChunkPlan.plan(listOf(1.6f, 5.0f), listOf(true, true), rtf = 1.5f))
        // The same line on a fast device (low RTF) keeps up, so the early split is kept.
        assertEquals(listOf(0..0, 1..1), ChunkPlan.plan(listOf(1.6f, 5.0f), listOf(true, true), rtf = 0.3f))
    }

    @Test
    fun tinyChunksAreNotWorthACut_evenOnAFastDevice() {
        assertEquals(listOf(0..2), ChunkPlan.plan(listOf(0.3f, 0.6f, 0.6f), List(3) { true }, rtf = 0.1f))
        // …and a chunk that would leave a < 1 s tail is not closed either.
        assertEquals(listOf(0..1), ChunkPlan.plan(listOf(2.0f, 0.5f), listOf(true, true), rtf = 0.1f))
    }
}
