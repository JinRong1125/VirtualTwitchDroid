package com.example.virtualtwitchdroid.feature.voice.lipsync

import com.example.virtualtwitchdroid.core.common.media.VisemeFrame
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class VisemeTrackTest {
    private val eps = 1e-3f

    // 「こんにちは」: ko N ni chi ha — a consonant+vowel, a closed N, then vowels; 0.1 s pre/post pads.
    private val konnichiwa = UtteranceTiming(
        phrases = listOf(
            PhraseTiming(
                moras = listOf(
                    MoraTiming(0.05f, "o", 0.10f), // ko
                    MoraTiming(null, "N", 0.08f), // N (closed)
                    MoraTiming(0.04f, "i", 0.09f), // ni
                    MoraTiming(0.06f, "i", 0.08f), // chi
                    MoraTiming(0.05f, "a", 0.12f), // ha
                ),
                pauseSeconds = null,
            ),
        ),
        prePhonemeSeconds = 0.1f,
        postPhonemeSeconds = 0.1f,
    )

    @Test
    fun closedBeforeSpeech_openOnTheVowel_closedAfterTheEnd() {
        val track = VisemeTrack.build(konnichiwa)
        assertEquals(VisemeFrame.CLOSED, track.sample(0f))
        assertEquals(VisemeFrame.CLOSED, track.sample(0.05f)) // still in the pre-phoneme pad
        // "ko": consonant 0.10–0.15, vowel o 0.15–0.25 → fully open shortly after the attack.
        val o = track.sample(0.20f)
        assertEquals(1f, o.oh, eps)
        assertEquals(0f, o.aa, eps)
        // Total: 0.1 + (0.15 + 0.08 + 0.13 + 0.14 + 0.17) + 0.1 = 0.87 s
        assertEquals(0.87f, track.durationSeconds, eps)
        assertEquals(VisemeFrame.CLOSED, track.sample(0.87f))
        assertEquals(VisemeFrame.CLOSED, track.sample(5f))
        assertEquals(VisemeFrame.CLOSED, track.sample(-1f))
    }

    @Test
    fun nClosesTheMouth_andAdjacentVowelsCrossfade_insteadOfClosing() {
        val track = VisemeTrack.build(konnichiwa)
        // N runs 0.25–0.33: closed by then.
        assertEquals(0f, track.sample(0.32f).oh, eps)
        // ni: 0.33–0.46 (vowel i from 0.37); chi: 0.46–0.60 (vowel i from 0.52): between the two vowels the
        // mouth never closes (both are "ih", so the crossfade stays at the same shape).
        for (t in listOf(0.42f, 0.46f, 0.49f, 0.55f)) {
            assertTrue(track.sample(t).ih > 0.5f, "ih should stay open at $t, got ${track.sample(t)}")
        }
        // ha: vowel a from 0.65 to 0.77 — fully open a, ih released.
        val a = track.sample(0.72f)
        assertEquals(1f, a.aa, eps)
        assertEquals(0f, a.ih, eps)
    }

    @Test
    fun devoicedVowel_isAReducedShape_andPausesClose() {
        val desu = UtteranceTiming(
            phrases = listOf(
                PhraseTiming(listOf(MoraTiming(0.05f, "e", 0.1f), MoraTiming(0.06f, "U", 0.05f)), pauseSeconds = 0.3f),
                PhraseTiming(listOf(MoraTiming(null, "a", 0.1f)), pauseSeconds = null),
            ),
            prePhonemeSeconds = 0f,
            postPhonemeSeconds = 0f,
        )
        val track = VisemeTrack.build(desu)
        assertEquals(VisemeTrack.DEVOICED_WEIGHT, track.sample(0.25f).ou, eps) // す devoiced: 0.4, not 0
        assertEquals(VisemeFrame.CLOSED, track.sample(0.45f)) // inside the 0.3 s pause
        assertTrue(track.sample(0.60f).aa > 0.9f) // 「あ」 after the pause opens again
    }

    @Test
    fun speedScale_compressesTheTimeline() {
        val normal = VisemeTrack.build(konnichiwa)
        val fast = VisemeTrack.build(konnichiwa.copy(speedScale = 2f))
        assertEquals(normal.durationSeconds / 2f, fast.durationSeconds, eps)
        assertEquals(normal.sample(0.20f), fast.sample(0.10f))
    }

    @Test
    fun emptyUtterance_isJustThePads() {
        val track = VisemeTrack.build(UtteranceTiming(emptyList(), 0.1f, 0.1f))
        assertEquals(0.2f, track.durationSeconds, eps)
        assertFalse(track.sample(0.1f) != VisemeFrame.CLOSED)
    }

    @Test
    fun timeline_anchorsTracksToUptime_andDropsFinishedOnes() {
        val timeline = VisemeTimeline()
        val track = VisemeTrack.build(konnichiwa)
        val anchor = 10_000_000_000L // 10 s uptime
        timeline.add(track, anchor)
        assertEquals(VisemeFrame.CLOSED, timeline.sample(anchor - 1_000_000_000L)) // not started yet
        assertTrue(timeline.isSpeaking(anchor - 1_000_000_000L)) // …but scheduled
        assertEquals(1f, timeline.sample(anchor + 200_000_000L).oh, eps) // 0.20 s in → "o"
        assertEquals(0.67f, timeline.remainingSeconds(anchor + 200_000_000L), 0.01f)
        assertEquals(VisemeFrame.CLOSED, timeline.sample(anchor + 2_000_000_000L)) // finished → dropped
        assertFalse(timeline.isSpeaking(anchor + 2_000_000_000L))
        assertEquals(0f, timeline.remainingSeconds(anchor + 2_000_000_000L))
    }
}
