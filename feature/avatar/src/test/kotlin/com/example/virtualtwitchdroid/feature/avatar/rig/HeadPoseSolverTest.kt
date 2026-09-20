package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class HeadPoseSolverTest {
    private val eps = 0.01f
    private val raw = HeadPose(yaw = 30f, pitch = -15f, roll = 10f)
    private val matrix = RotationMath.rotationMatrix(raw)

    private fun assertPose(expected: HeadPose, actual: HeadPose) {
        assertEquals(expected.yaw, actual.yaw, eps, "yaw")
        assertEquals(expected.pitch, actual.pitch, eps, "pitch")
        assertEquals(expected.roll, actual.roll, eps, "roll")
    }

    @Test
    fun noMatrix_yieldsIdentity() {
        assertEquals(HeadPose.IDENTITY, HeadPoseSolver().solve(null))
    }

    @Test
    fun unmirrored_returnsTheDecomposedAnglesAsIs() {
        assertPose(raw, HeadPoseSolver(mirror = false).solve(matrix))
    }

    @Test
    fun mirror_negatesYawAndRoll_butNotPitch() {
        assertPose(HeadPose(-30f, -15f, -10f), HeadPoseSolver(mirror = true).solve(matrix))
    }

    @Test
    fun vrm0Conversion_negatesPitchAndRoll_keepsYaw_andComposesWithMirror() {
        // A −Z-facing rig is our frame turned 180° about Y: X and Z axes flip, Y (yaw) is shared.
        assertPose(HeadPose(30f, 15f, -10f), raw.forModelFacingNegativeZ())
        // Mirror first (yaw, roll flip), then the model conversion (pitch, roll flip): roll flips back.
        assertPose(HeadPose(-30f, 15f, 10f), HeadPoseSolver(mirror = true).solve(matrix).forModelFacingNegativeZ())
    }

    @Test
    fun vrm0_noddingDown_rotatesTheMinusZFaceDownward() {
        // Sanity of the sign: for a bone whose forward is −Z, the converted pitch must move that forward
        // vector toward −Y (down), exactly as the raw pitch moves a +Z forward vector down.
        val nodDown = HeadPose(yaw = 0f, pitch = 20f, roll = 0f)
        val plusZ = RotationMath.rotationMatrix(nodDown).let { m -> m[9] } // y-component of R·(0,0,1)
        // y-component of R'·(0,0,−1) for the converted pose
        val minusZ = RotationMath.rotationMatrix(nodDown.forModelFacingNegativeZ()).let { m -> -m[9] }
        assertEquals(plusZ, minusZ, eps)
        assertTrue(plusZ < 0f, "a positive pitch must point the face down")
    }

    @Test
    fun vrm0_turningLeft_swingsTheMinusZFaceToTheModelsLeft() {
        // Subject turns to their left (positive yaw). +Z face → +X (its left); −Z face → −X (ITS left).
        val turnLeft = HeadPose(yaw = 20f, pitch = 0f, roll = 0f)
        val plusZ = RotationMath.rotationMatrix(turnLeft).let { m -> m[8] } // x-component of R·(0,0,1)
        val minusZ = RotationMath.rotationMatrix(turnLeft.forModelFacingNegativeZ()).let { m -> -m[8] }
        assertTrue(plusZ > 0f && minusZ < 0f, "left is +X for a +Z face and −X for a −Z face")
        assertEquals(plusZ, -minusZ, eps)
    }

    @Test
    fun offset_isTheTranslationColumn_inCentimetres_mirrorFlipsX() {
        val m = matrix.copyOf().also {
            it[12] = 3f // x (subject's left, cm)
            it[13] = -2f // y
            it[14] = -45f // z (45 cm from the camera)
        }
        assertEquals(HeadOffset(3f, -2f, -45f), HeadPoseSolver(mirror = false).solveOffset(m))
        assertEquals(HeadOffset(-3f, -2f, -45f), HeadPoseSolver(mirror = true).solveOffset(m))
        assertEquals(null, HeadPoseSolver().solveOffset(null)) // unknown, not "at the origin"
    }
}
