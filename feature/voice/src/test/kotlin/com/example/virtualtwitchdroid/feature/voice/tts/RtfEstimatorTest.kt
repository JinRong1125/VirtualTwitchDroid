package com.example.virtualtwitchdroid.feature.voice.tts

import kotlin.test.assertEquals
import org.junit.Test

class RtfEstimatorTest {
    @Test
    fun startsPessimistic_subtractsTheCallOverhead_andConverges() {
        val e = RtfEstimator()
        assertEquals(RtfEstimator.INITIAL_RTF, e.estimate)
        // 1.7 s to make 1.0 s of audio = 0.7 s overhead + 1.0 s/s marginal.
        repeat(20) { e.update(synthesisSeconds = 1.7f, audioSeconds = 1.0f) }
        assertEquals(1.0f, e.estimate, 0.01f)
        e.update(synthesisSeconds = 0f, audioSeconds = 0f) // ignored
        assertEquals(1.0f, e.estimate, 0.01f)
        // A call cheaper than its overhead is floored, never negative.
        repeat(20) { e.update(synthesisSeconds = 0.5f, audioSeconds = 2f) }
        assertEquals(RtfEstimator.MIN_RTF, e.estimate, 0.01f)
    }
}
