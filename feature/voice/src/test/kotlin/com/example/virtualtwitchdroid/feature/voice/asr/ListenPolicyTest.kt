package com.example.virtualtwitchdroid.feature.voice.asr

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ListenPolicyTest {
    @Test
    fun acceptsEverything_whileTheMonitorIsSilent() {
        val p = ListenPolicy()
        assertTrue(p.accepts(0L, 500L))
        assertTrue(p.accepts(9_000L, 10_000L))
    }

    @Test
    fun rejectsSegmentsOverlappingPlayback_orItsTail_acceptsOnesEntirelyBefore() {
        val p = ListenPolicy(tailMs = 300)
        p.monitorSpeaking(true, nowMs = 1_000)
        assertFalse(p.accepts(1_200, 1_500)) // fully inside playback
        assertFalse(p.accepts(800, 1_100)) // started before, ended during
        assertTrue(p.accepts(200, 900)) // ended before Zundamon started
        p.monitorSpeaking(false, nowMs = 2_000)
        assertFalse(p.accepts(1_900, 2_200)) // straddles the end + reverberation tail
        assertFalse(p.accepts(2_100, 2_250)) // inside the tail
        assertTrue(p.accepts(2_301, 2_800)) // after the tail
    }
}
