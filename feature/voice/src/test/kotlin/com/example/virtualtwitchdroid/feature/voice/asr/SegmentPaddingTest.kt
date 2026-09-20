package com.example.virtualtwitchdroid.feature.voice.asr

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class SegmentPaddingTest {
    @Test
    fun padsSilenceBeforeAndAfter_keepingTheSpeechIntact() {
        val speech = FloatArray(1_000) { 0.5f }
        val padded = SegmentPadding.pad(speech, sampleRate = 16_000)
        assertEquals(6_400 + 1_000 + 4_800, padded.size) // 0.4 s + speech + 0.3 s
        assertEquals(0f, padded[0])
        assertEquals(0f, padded[6_399])
        assertContentEquals(speech, padded.copyOfRange(6_400, 7_400))
        assertEquals(0f, padded.last())
    }

    @Test
    fun noPadding_returnsTheSameArray() {
        val speech = FloatArray(10) { 1f }
        assertSame(speech, SegmentPadding.pad(speech, 16_000, lead = 0f, tail = 0f))
    }
}
