package com.example.virtualtwitchdroid.feature.avatar

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.virtualtwitchdroid.core.testing.util.MainDispatcherRule
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTrackingFrame
import com.example.virtualtwitchdroid.feature.avatar.tracking.FakeFaceTracker
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStats
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

// Turbine's default 3s is wall-clock and flakes under the parallel unit run (see BrowseViewModelTest).
private val TURBINE_TIMEOUT = 30.seconds

class AvatarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tracker = FakeFaceTracker()
    private var now = 1_000L
    private val viewModel = AvatarViewModel(tracker) { now }

    /** A detected frame with only the subject's left eye closed (an asymmetric, mirror-sensitive signal). */
    private fun winkFrame(timestampMs: Long) = FaceTrackingFrame(
        blendshapes = mapOf(ArkitBlendshape.EYE_BLINK_LEFT.key to 1f),
        headMatrixColumnMajor = null,
        timestampMs = timestampMs,
        faceDetected = true,
    )

    private suspend fun ReceiveTurbine<AvatarUiState>.awaitUntil(predicate: (AvatarUiState) -> Boolean): AvatarUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun detectedFrame_isMirroredForTheFrontLens_byDefault() = runTest {
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            tracker.frames.value = winkFrame(timestampMs = 1_100)
            val state = awaitUntil { it.rig.faceDetected }
            // The streamer winked their LEFT eye; in the selfie mirror it shows on the avatar's RIGHT.
            assertTrue(state.rig[VrmExpression.BLINK_RIGHT] > 0f)
            assertEquals(0f, state.rig[VrmExpression.BLINK_LEFT])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun frameWithNoSuccessor_relaxesToNoFace_afterTheStaleTimeout() = runTest {
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            tracker.frames.value = winkFrame(timestampMs = 1_100)
            awaitUntil { it.rig.faceDetected }

            advanceTimeBy(AvatarViewModel.STALE_FRAME_MS + 1)
            runCurrent()

            val relaxed = awaitUntil { !it.rig.faceDetected }
            assertFalse(relaxed.rig.faceDetected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleCamera_ignoresFramesCapturedBeforeTheSwitch_andStopsMirroring() = runTest {
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            tracker.frames.value = winkFrame(timestampMs = 1_100)
            awaitUntil { it.rig.faceDetected }

            now = 2_000
            viewModel.toggleCamera() // → back lens, since t = 2000
            assertFalse(viewModel.frontCamera.value)
            // The old lens's last frame (t = 1100) must NOT be re-mapped through the new mirror setting.
            val stale = awaitUntil { !it.rig.faceDetected }
            assertEquals(0f, stale.rig[VrmExpression.BLINK_LEFT])
            assertEquals(0f, stale.rig[VrmExpression.BLINK_RIGHT])

            tracker.frames.value = winkFrame(timestampMs = 2_500)
            val fresh = awaitUntil { it.rig.faceDetected }
            // Back lens: no mirror — the left wink stays on the left.
            assertTrue(fresh.rig[VrmExpression.BLINK_LEFT] > 0f)
            assertEquals(0f, fresh.rig[VrmExpression.BLINK_RIGHT])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stats_decayToZero_whenResultsStop() = runTest {
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            tracker.stats.value = TrackerStats(fps = 30f, inferenceMs = 20)
            awaitUntil { it.stats.fps == 30f }

            advanceTimeBy(AvatarViewModel.STALE_STATS_MS + 1)
            runCurrent()

            awaitUntil { it.stats == TrackerStats.EMPTY }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun trackerStatus_isSurfaced() = runTest {
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            tracker.status.value = TrackerStatus.Failed(TrackerStatus.FailureReason.INIT_FAILED)
            val state = awaitUntil { it.status is TrackerStatus.Failed }
            assertEquals(TrackerStatus.Failed(TrackerStatus.FailureReason.INIT_FAILED), state.status)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
