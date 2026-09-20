package com.example.virtualtwitchdroid.feature.voice.tts

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class UtteranceQueueTest {
    @Test
    fun keepsAtMostMaxPending_droppingTheOldest() {
        val q = UtteranceQueue(maxPending = 2)
        q.offer("一")
        q.offer("二")
        q.offer("三")
        assertEquals(2, q.size)
        assertEquals(1, q.dropped)
        assertEquals("二", q.poll())
        assertEquals("三", q.poll())
        assertNull(q.poll())
    }

    @Test
    fun speedScale_isNormalUpToFourSeconds_thenRampsToTheCeiling() {
        assertEquals(1f, UtteranceQueue.speedScaleFor(0f))
        assertEquals(1f, UtteranceQueue.speedScaleFor(4f))
        assertEquals(1.15f, UtteranceQueue.speedScaleFor(6f), 1e-4f)
        assertEquals(1.3f, UtteranceQueue.speedScaleFor(8f), 1e-4f)
        assertEquals(1.3f, UtteranceQueue.speedScaleFor(30f), 1e-4f)
    }
}
