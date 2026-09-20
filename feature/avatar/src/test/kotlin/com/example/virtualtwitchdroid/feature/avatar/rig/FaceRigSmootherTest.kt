package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class FaceRigSmootherTest {
    private val eps = 0.001f

    private fun rig(aa: Float, yaw: Float = 0f) =
        FaceRig(mapOf(VrmExpression.AA to aa), HeadPose(yaw, 0f, 0f), faceDetected = true)

    @Test
    fun firstSample_passesThroughUnchanged() {
        val first = rig(aa = 0.8f, yaw = 30f)
        assertEquals(first, FaceRigSmoother(alpha = 0.5f).next(first))
    }

    @Test
    fun headOffset_isSmoothedLikeTheAngles() {
        val smoother = FaceRigSmoother(alpha = 0.5f)
        smoother.next(rig(0f).copy(headOffset = HeadOffset.ZERO))
        val out = smoother.next(rig(0f).copy(headOffset = HeadOffset(10f, -4f, 2f)))
        assertEquals(5f, out.headOffset!!.x, eps)
        assertEquals(-2f, out.headOffset!!.y, eps)
        assertEquals(1f, out.headOffset!!.z, eps)
        // An unknown previous position is not smoothed toward: the first known value stands.
        val fresh = FaceRigSmoother(alpha = 0.5f)
        fresh.next(rig(0f)) // headOffset == null
        assertEquals(
            HeadOffset(10f, -4f, 2f),
            fresh.next(rig(0f).copy(headOffset = HeadOffset(10f, -4f, 2f))).headOffset,
        )
    }

    @Test
    fun alphaOfOne_isNoSmoothing() {
        val smoother = FaceRigSmoother(alpha = 1f)
        smoother.next(rig(0f))
        assertEquals(1f, smoother.next(rig(1f))[VrmExpression.AA], eps)
    }

    @Test
    fun stepInput_movesByAlphaTowardTheTarget_onEveryChannel() {
        val smoother = FaceRigSmoother(alpha = 0.5f)
        smoother.next(rig(aa = 0f, yaw = 0f))
        val out = smoother.next(rig(aa = 1f, yaw = 40f))
        assertEquals(0.5f, out[VrmExpression.AA], eps)
        assertEquals(20f, out.head.yaw, eps)
    }

    @Test
    fun constantInput_convergesToTheTarget() {
        val smoother = FaceRigSmoother(alpha = 0.3f)
        smoother.next(rig(0f))
        var out = rig(0f)
        repeat(40) { out = smoother.next(rig(1f)) }
        assertEquals(1f, out[VrmExpression.AA], 0.001f)
    }

    @Test
    fun expressionThatDisappears_decaysTowardZero_ratherThanSnapping() {
        val smoother = FaceRigSmoother(alpha = 0.5f)
        smoother.next(rig(1f))
        val out = smoother.next(FaceRig.NEUTRAL.copy(faceDetected = true)) // AA no longer reported
        assertEquals(0.5f, out[VrmExpression.AA], eps)
    }

    @Test
    fun reset_forgetsHistory_soTheNextSamplePassesThrough() {
        val smoother = FaceRigSmoother(alpha = 0.5f)
        smoother.next(rig(0f))
        smoother.reset()
        assertEquals(1f, smoother.next(rig(1f))[VrmExpression.AA], eps)
    }

    @Test
    fun rejectsAlphaOutsideZeroExclusiveToOne() {
        assertFailsWith<IllegalArgumentException> { FaceRigSmoother(alpha = 0f) }
        assertFailsWith<IllegalArgumentException> { FaceRigSmoother(alpha = 1.5f) }
    }
}
