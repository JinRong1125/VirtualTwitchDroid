package com.example.virtualtwitchdroid.feature.publish

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Exponential-backoff schedule for re-opening a dropped broadcast connection. Kept as a pure,
 * deterministic value object so the retry cadence can be unit-tested without a live network — the
 * [PublishController] just asks it "how long before attempt N, or should I give up?".
 *
 * Expressed with [kotlin.time.Duration] rather than bare `Long` milliseconds so the unit is
 * self-documenting and the value feeds straight into the `delay(Duration)` coroutine overload.
 */
internal class ReconnectPolicy(
    private val maxAttempts: Int = 5,
    private val baseDelay: Duration = 1.seconds,
    private val maxDelay: Duration = 16.seconds,
) {
    /**
     * The delay before the given 1-based [attempt], or `null` once [attempt] passes [maxAttempts]
     * (i.e. stop retrying). The delay doubles each attempt (`base`, `2·base`, `4·base`, …), capped
     * at [maxDelay].
     */
    fun delayForAttempt(attempt: Int): Duration? {
        if (attempt < 1 || attempt > maxAttempts) return null
        // Keep the shift inside positive Int range; [Duration] saturates instead of wrapping on a
        // huge product and [minOf] applies the ceiling, so no negative-overflow guard is needed.
        val doublings = (attempt - 1).coerceAtMost(30)
        val raw = baseDelay * (1 shl doublings)
        return minOf(raw, maxDelay)
    }
}
