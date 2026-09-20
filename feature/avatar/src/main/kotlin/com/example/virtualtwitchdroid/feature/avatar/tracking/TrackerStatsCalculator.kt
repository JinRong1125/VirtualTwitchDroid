package com.example.virtualtwitchdroid.feature.avatar.tracking

import java.util.ArrayDeque

/**
 * Pure bookkeeping behind [TrackerStats]: a one-second sliding window of result timestamps gives fps;
 * `result − capture` gives the per-frame inference latency. Not thread-safe — call from one thread
 * (the tracker's result callback).
 */
class TrackerStatsCalculator(private val windowMs: Long = 1_000L) {
    private val resultTimes = ArrayDeque<Long>()

    /**
     * Records that a frame captured at [captureTimestampMs] produced its result at [resultTimestampMs]
     * (same clock) and returns the updated stats.
     */
    fun record(captureTimestampMs: Long, resultTimestampMs: Long): TrackerStats {
        resultTimes.addLast(resultTimestampMs)
        while (resultTimes.isNotEmpty() && resultTimestampMs - resultTimes.first() >= windowMs) {
            resultTimes.removeFirst()
        }
        val fps = if (resultTimes.size < 2) {
            resultTimes.size.toFloat()
        } else {
            // n results spanning (last − first) ms → (n − 1) intervals; scale the span to a full window.
            val spanMs = (resultTimes.last() - resultTimes.first()).coerceAtLeast(1L)
            (resultTimes.size - 1) * 1_000f / spanMs
        }
        return TrackerStats(fps = fps, inferenceMs = (resultTimestampMs - captureTimestampMs).coerceAtLeast(0L))
    }

    fun reset() = resultTimes.clear()
}
