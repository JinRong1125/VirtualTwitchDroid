package com.example.virtualtwitchdroid.feature.avatar.tracking

import kotlin.test.assertEquals
import org.junit.Test

class TrackerStatsCalculatorTest {

    @Test
    fun inferenceLatency_isResultMinusCapture_neverNegative() {
        val calc = TrackerStatsCalculator()
        assertEquals(18L, calc.record(captureTimestampMs = 1_000, resultTimestampMs = 1_018).inferenceMs)
        assertEquals(0L, calc.record(captureTimestampMs = 2_000, resultTimestampMs = 1_990).inferenceMs)
    }

    @Test
    fun fps_isMeasuredFromTheSlidingOneSecondWindow() {
        val calc = TrackerStatsCalculator()
        // A steady 33 ms cadence ≈ 30 fps once the window has filled.
        var stats = TrackerStats.EMPTY
        for (i in 0 until 40) {
            val t = 1_000L + i * 33L
            stats = calc.record(captureTimestampMs = t - 10, resultTimestampMs = t)
        }
        assertEquals(30.3f, stats.fps, 0.5f)
    }

    @Test
    fun fps_dropsWhenResultsSlowDown() {
        val calc = TrackerStatsCalculator()
        for (i in 0 until 30) calc.record(1_000L + i * 33L - 10, 1_000L + i * 33L)
        // Then a long stall: only the recent results survive the window.
        val stats = calc.record(captureTimestampMs = 4_990, resultTimestampMs = 5_000)
        assertEquals(1f, stats.fps, 0.001f) // a single result in the window
    }

    @Test
    fun reset_clearsTheWindow() {
        val calc = TrackerStatsCalculator()
        for (i in 0 until 10) calc.record(1_000L + i * 33L - 10, 1_000L + i * 33L)
        calc.reset()
        assertEquals(1f, calc.record(2_000, 2_010).fps, 0.001f)
    }
}
