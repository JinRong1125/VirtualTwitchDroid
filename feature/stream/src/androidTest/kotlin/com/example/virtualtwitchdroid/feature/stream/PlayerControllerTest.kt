package com.example.virtualtwitchdroid.feature.stream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Exercises [PlayerController]'s real ExoPlayer error/retry state machine on-device (ExoPlayer needs
 * an Android runtime + a Looper thread, so this can't be a plain JVM test). A deliberately invalid
 * media URL drives a genuine `onPlayerError`, letting us assert [PlayerController.playbackError]
 * flips true and that [PlayerController.retryPlayback] clears it.
 */
class PlayerControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: PlayerController

    // ExoPlayer must be created and touched on a Looper thread — use the instrumentation main thread.
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Before
    fun setUp() = onMain { controller = PlayerController(context) }

    @After
    fun tearDown() = onMain { controller.release() }

    @Test
    fun playbackError_isFalseInitially() {
        assertFalse(controller.playbackError.value)
    }

    @Test
    fun playbackError_setOnBadMedia_thenClearedByRetry() {
        // A host that never resolves → the load fails → onPlayerError → playbackError becomes true.
        onMain { controller.bind("test", 0, "https://invalid.invalid/nonexistent.m3u8") }
        assertTrue(
            awaitPlaybackError(expected = true),
            "playbackError should flip true after ExoPlayer fails to load the bad URL",
        )

        // Retry must clear the error window. (It intentionally does NOT re-prepare stale media —
        // a regression that re-prepared here would risk re-raising the just-dismissed error.)
        onMain { controller.retryPlayback() }
        assertFalse(controller.playbackError.value)
    }

    /** Polls the [PlayerController.playbackError] flow up to [timeoutMs] for [expected]. */
    private fun awaitPlaybackError(expected: Boolean, timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (controller.playbackError.value == expected) return true
            Thread.sleep(100)
        }
        return controller.playbackError.value == expected
    }
}
