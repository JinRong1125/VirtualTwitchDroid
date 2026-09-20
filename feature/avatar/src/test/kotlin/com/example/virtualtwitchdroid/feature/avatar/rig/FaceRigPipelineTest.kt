package com.example.virtualtwitchdroid.feature.avatar.rig

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTrackingFrame
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Turbine's default 3s is wall-clock and flakes under the parallel unit run (see BrowseViewModelTest).
private val TURBINE_TIMEOUT = 30.seconds

/**
 * The shared frames → rig pipeline (the ViewModel and the broadcast session both sit on it). Smoothing is
 * disabled (`alpha = 1`) so the mapped weights can be asserted exactly.
 */
class FaceRigPipelineTest {

    private var now = 1_000L
    private val frames = MutableStateFlow(FaceTrackingFrame.noFace(timestampMs = 0L))
    private val pipeline = FaceRigPipeline(alpha = 1f, staleFrameMs = STALE_MS) { now }

    /** Jaw open (symmetric) + only the subject's LEFT eye closed (mirror-sensitive). */
    private fun frame(timestampMs: Long) = FaceTrackingFrame(
        blendshapes = mapOf(ArkitBlendshape.JAW_OPEN.key to 0.5f, ArkitBlendshape.EYE_BLINK_LEFT.key to 1f),
        headMatrixColumnMajor = null,
        timestampMs = timestampMs,
        faceDetected = true,
    )

    private suspend fun ReceiveTurbine<FaceRig>.awaitUntil(predicate: (FaceRig) -> Boolean): FaceRig {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun frontLensByDefault_mapsAndMirrors() = runTest {
        pipeline.rigs(frames).test(timeout = TURBINE_TIMEOUT) {
            frames.value = frame(timestampMs = 1_100)
            val rig = awaitUntil { it.faceDetected }
            assertEquals(0.5f, rig[VrmExpression.AA])
            // The streamer winked their LEFT eye; in the selfie mirror it lands on the avatar's RIGHT.
            assertEquals(1f, rig[VrmExpression.BLINK_RIGHT])
            assertEquals(0f, rig[VrmExpression.BLINK_LEFT])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stalledFrames_relaxToNoFace() = runTest {
        pipeline.rigs(frames).test(timeout = TURBINE_TIMEOUT) {
            frames.value = frame(timestampMs = 1_100)
            awaitUntil { it.faceDetected }

            advanceTimeBy(STALE_MS + 1)
            runCurrent()

            val relaxed = awaitUntil { !it.faceDetected }
            assertTrue(relaxed.expressions.values.all { it == 0f }) // every weight back to neutral
            assertEquals(HeadPose.IDENTITY, relaxed.head)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setLens_dropsOlderFrames_andStopsMirroringOnTheBackLens() = runTest {
        pipeline.rigs(frames).test(timeout = TURBINE_TIMEOUT) {
            frames.value = frame(timestampMs = 1_100)
            awaitUntil { it.faceDetected }

            now = 2_000
            pipeline.setLens(front = false)
            // The front lens's last frame (t = 1100) must not be re-mapped through the new lens.
            val dropped = awaitUntil { !it.faceDetected }
            assertFalse(dropped.faceDetected)

            frames.value = frame(timestampMs = 2_500)
            val fresh = awaitUntil { it.faceDetected }
            assertEquals(1f, fresh[VrmExpression.BLINK_LEFT]) // no mirror on the back lens
            assertEquals(0f, fresh[VrmExpression.BLINK_RIGHT])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun smoothing_blendsTowardTheNewestFrame() = runTest {
        val smoothed = FaceRigPipeline(alpha = 0.5f, staleFrameMs = STALE_MS) { now }
        smoothed.rigs(frames).test(timeout = TURBINE_TIMEOUT) {
            // The initial no-face sample (all zeros) is the smoother's first sample, so the first detected
            // frame is already blended: 0 + 0.5 · (0.5 − 0) = 0.25.
            frames.value = frame(timestampMs = 1_100)
            val first = awaitUntil { it.faceDetected }
            assertEquals(0.25f, first[VrmExpression.AA])

            frames.value = frame(timestampMs = 1_200).copy(blendshapes = mapOf(ArkitBlendshape.JAW_OPEN.key to 1f))
            val second = awaitUntil { it[VrmExpression.AA] > 0.25f }
            assertEquals(0.625f, second[VrmExpression.AA]) // 0.25 + 0.5 · (1 − 0.25)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val STALE_MS = 300L
    }
}
