package com.example.virtualtwitchdroid.feature.publish

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The Zundamon audio source's contract as a `PcmSink`: format follows the encoder's `init`, it is not
 * consuming until RootEncoder starts it (off-air), and writes then are dropped (reported as accepted) so the
 * voice never blocks on a source nobody drains. Starting needs an encoder thread, so it is not exercised here.
 */
class ZundamonAudioSourceTest {
    @Test
    fun init_setsTheSinkFormat_andItIsNotConsumingOffAir() {
        val source = ZundamonAudioSource()
        assertTrue(source.init(44_100, true, false, false))
        assertEquals(44_100, source.sink.sampleRate)
        assertTrue(source.sink.stereo)
        assertEquals(ZundamonAudioSource.LATENCY_MS, source.sink.latencyMs)
        assertFalse(source.sink.consuming)
        assertFalse(source.isRunning())
    }

    @Test
    fun write_whileNotRunning_isDroppedButReportedAccepted_andNothingIsQueued() {
        val source = ZundamonAudioSource(uptimeMs = { 0L })
        source.init(44_100, true, false, false)
        assertEquals(1_024, source.sink.write(ByteArray(1_024), 0, 1_024))
        assertEquals(0, source.sink.queuedMs)
    }
}
