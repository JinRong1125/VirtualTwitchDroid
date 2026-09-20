package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import org.junit.Test

class EyeLookAtTest {
    private val eps = 1e-4f

    /** Distinct scales per map so the test can tell which one each eye read. */
    private val lookAt = VrmLookAt(
        LookAtType.BONE,
        horizontalInner = LookAtRangeMap(inputMaxValue = 90f, outputScale = 8f),
        horizontalOuter = LookAtRangeMap(inputMaxValue = 90f, outputScale = 12f),
        verticalDown = LookAtRangeMap(inputMaxValue = 90f, outputScale = 10f),
        verticalUp = LookAtRangeMap(inputMaxValue = 90f, outputScale = 6f),
    )

    private fun rig(vararg weights: Pair<VrmExpression, Float>) =
        FaceRig(weights.toMap(), HeadPose.IDENTITY, faceDetected = true)

    @Test
    fun straightGaze_isIdentityOnBothEyes() {
        assertEquals(EyeLookAt.Gaze.STRAIGHT, EyeLookAt.solve(FaceRig.NEUTRAL, lookAt))
    }

    @Test
    fun lookingLeft_leftEyeReadsOuter_rightEyeReadsInner_bothPositiveYaw() {
        val gaze = EyeLookAt.solve(rig(VrmExpression.LOOK_LEFT to 1f), lookAt)
        assertEquals(12f, gaze.left.yaw, eps) // outer: away from the nose
        assertEquals(8f, gaze.right.yaw, eps) // inner: toward the nose
        assertEquals(0f, gaze.left.pitch, eps)
        assertEquals(0f, gaze.left.roll, eps)
    }

    @Test
    fun lookingRight_mirrorsTheMaps_andTheSign() {
        val gaze = EyeLookAt.solve(rig(VrmExpression.LOOK_RIGHT to 0.5f), lookAt)
        assertEquals(-4f, gaze.left.yaw, eps) // inner, half a glance
        assertEquals(-6f, gaze.right.yaw, eps) // outer
    }

    @Test
    fun vertical_downIsPositivePitch_upIsNegative_sameOnBothEyes() {
        val down = EyeLookAt.solve(rig(VrmExpression.LOOK_DOWN to 1f), lookAt)
        assertEquals(10f, down.left.pitch, eps)
        assertEquals(10f, down.right.pitch, eps)
        val up = EyeLookAt.solve(rig(VrmExpression.LOOK_UP to 1f), lookAt)
        assertEquals(-6f, up.left.pitch, eps)
        assertEquals(-6f, up.right.pitch, eps)
    }

    @Test
    fun opposingWeights_cancel_andNeverExceedTheOutputScale() {
        val cancel = EyeLookAt.solve(rig(VrmExpression.LOOK_LEFT to 0.7f, VrmExpression.LOOK_RIGHT to 0.7f), lookAt)
        assertEquals(0f, cancel.left.yaw, eps)
        val over = EyeLookAt.solve(rig(VrmExpression.LOOK_LEFT to 3f), lookAt) // a glitchy tracker value
        assertEquals(12f, over.left.yaw, eps)
    }

    @Test
    fun rangeMap_clampsAtInputMax_andScalesLinearlyBelowIt() {
        val map = LookAtRangeMap(inputMaxValue = 30f, outputScale = 10f)
        assertEquals(5f, map.map(15f), eps)
        assertEquals(5f, map.map(-15f), eps) // magnitude only
        assertEquals(10f, map.map(90f), eps)
        assertEquals(0f, LookAtRangeMap(0f, 10f).map(45f), eps) // degenerate: never divide by zero
    }
}
