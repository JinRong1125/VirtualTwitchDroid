package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class OffsetBaselineTest {

    private fun detected(x: Float, y: Float = 0f, z: Float = -40f) =
        FaceRig(emptyMap(), HeadPose.IDENTITY, faceDetected = true, headOffset = HeadOffset(x, y, z))

    private val baseline = OffsetBaseline(driftTimeConstantMs = 20_000L, recaptureAfterMs = 2_000L)

    @Test
    fun firstDetection_becomesNeutral_offsetIsZero() {
        val out = baseline.relative(detected(x = 5f, z = -40f), timestampMs = 1_000)
        assertEquals(HeadOffset.ZERO, out.headOffset)
    }

    @Test
    fun movingAfterCapture_reportsTheDisplacement() {
        baseline.relative(detected(x = 5f), timestampMs = 1_000)
        val out = baseline.relative(detected(x = 15f), timestampMs = 1_033)
        // 10 cm to the left, minus the tiny drift of the baseline in 33 ms (< 0.02 cm).
        assertEquals(10f, out.headOffset!!.x, 0.05f)
        assertEquals(0f, out.headOffset!!.z, 0.05f)
    }

    @Test
    fun noFace_reportsZero_andKeepsNeutral_acrossAShortDropout() {
        baseline.relative(detected(x = 5f), timestampMs = 1_000)
        val lost = baseline.relative(FaceRig.NEUTRAL, timestampMs = 1_500)
        assertEquals(HeadOffset.ZERO, lost.headOffset)
        // Back after 1 s (< 2 s): the old neutral still applies, so the same move still reads as +10.
        val back = baseline.relative(detected(x = 15f), timestampMs = 2_000)
        assertEquals(10f, back.headOffset!!.x, 0.6f) // (1 s of drift toward +15 shaves off ~0.5 cm)
    }

    @Test
    fun faceLostForLong_recapturesNeutral() {
        baseline.relative(detected(x = 5f), timestampMs = 1_000)
        val back = baseline.relative(detected(x = 15f), timestampMs = 4_000) // gone for 3 s
        assertEquals(HeadOffset.ZERO, back.headOffset) // +15 is the new neutral
        assertEquals(0f, baseline.relative(detected(x = 15f), timestampMs = 4_033).headOffset!!.x, 0.05f)
    }

    @Test
    fun sustainedShift_driftsToBecomeTheNewNeutral() {
        baseline.relative(detected(x = 0f), timestampMs = 0)
        var t = 0L
        var out = detected(0f)
        repeat(30 * 60) {
            // one minute at 30 fps, sitting 10 cm to the left
            t += 33
            out = baseline.relative(detected(x = 10f), timestampMs = t)
        }
        // After 3 time constants only e^-3 ≈ 5 % of the lean remains.
        assertTrue(out.headOffset!!.x < 0.6f, "expected the lean to have drifted away, got ${out.headOffset!!.x}")
    }

    @Test
    fun detectedFrameWithoutAPosition_neverBecomesNeutral() {
        val noMatrix = FaceRig(emptyMap(), HeadPose.IDENTITY, faceDetected = true) // headOffset == null
        assertEquals(null, baseline.relative(noMatrix, timestampMs = 1_000).headOffset)
        // The first real position is the neutral one — not the origin the matrix-less frame would have implied.
        assertEquals(HeadOffset.ZERO, baseline.relative(detected(x = 5f, z = -40f), timestampMs = 1_033).headOffset)
        assertEquals(10f, baseline.relative(detected(x = 15f, z = -40f), timestampMs = 1_066).headOffset!!.x, 0.05f)
    }

    @Test
    fun reset_recapturesOnTheNextFrame() {
        baseline.relative(detected(x = 5f), timestampMs = 1_000)
        baseline.reset()
        assertEquals(HeadOffset.ZERO, baseline.relative(detected(x = 15f), timestampMs = 1_033).headOffset)
    }
}
