package com.example.virtualtwitchdroid.feature.publish

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun backoff_doubles_each_attempt() {
        val policy = ReconnectPolicy(maxAttempts = 5, baseDelay = 1.seconds, maxDelay = 16.seconds)
        assertEquals(1.seconds, policy.delayForAttempt(1))
        assertEquals(2.seconds, policy.delayForAttempt(2))
        assertEquals(4.seconds, policy.delayForAttempt(3))
        assertEquals(8.seconds, policy.delayForAttempt(4))
        assertEquals(16.seconds, policy.delayForAttempt(5))
    }

    @Test
    fun backoff_is_capped_at_max_delay() {
        val policy = ReconnectPolicy(maxAttempts = 10, baseDelay = 1.seconds, maxDelay = 5.seconds)
        // 4th attempt would be 8s but is capped to the 5s ceiling.
        assertEquals(5.seconds, policy.delayForAttempt(4))
        assertEquals(5.seconds, policy.delayForAttempt(9))
    }

    @Test
    fun gives_up_past_max_attempts_or_below_one() {
        val policy = ReconnectPolicy(maxAttempts = 3)
        assertNull(policy.delayForAttempt(4))
        assertNull(policy.delayForAttempt(0))
        assertNull(policy.delayForAttempt(-1))
    }
}
