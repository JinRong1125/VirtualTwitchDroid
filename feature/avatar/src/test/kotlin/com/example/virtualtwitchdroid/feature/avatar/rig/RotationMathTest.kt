package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Round-trips [RotationMath.rotationMatrix] → [RotationMath.toHeadPose] for exactness, plus one
 * hand-written **column-major** literal so a transposed layout could not pass by symmetry.
 */
class RotationMathTest {
    private val eps = 0.01f

    private fun assertPose(expected: HeadPose, actual: HeadPose) {
        assertEquals(expected.yaw, actual.yaw, eps, "yaw")
        assertEquals(expected.pitch, actual.pitch, eps, "pitch")
        assertEquals(expected.roll, actual.roll, eps, "roll")
    }

    @Test
    fun identityMatrix_decomposesToZeroAngles() {
        val identity = FloatArray(16).also {
            it[0] = 1f
            it[5] = 1f
            it[10] = 1f
            it[15] = 1f
        }
        assertPose(HeadPose.IDENTITY, RotationMath.toHeadPose(identity))
    }

    @Test
    fun handWrittenColumnMajorYaw30_decodesAsYaw30_notItsTranspose() {
        // Ry(30°) in column-major: column 0 = (cos, 0, -sin), column 2 = (sin, 0, cos).
        // A row-major reading of the same floats would decode as yaw −30°.
        val c = 0.8660254f
        val s = 0.5f
        val m = FloatArray(16)
        m[0] = c
        m[2] = -s // row 2, col 0
        m[5] = 1f
        m[8] = s // row 0, col 2
        m[10] = c
        m[15] = 1f
        assertPose(HeadPose(yaw = 30f, pitch = 0f, roll = 0f), RotationMath.toHeadPose(m))
    }

    @Test
    fun singleAxisRotations_roundTrip() {
        for (pose in listOf(HeadPose(30f, 0f, 0f), HeadPose(0f, -15f, 0f), HeadPose(0f, 0f, 10f))) {
            assertPose(pose, RotationMath.toHeadPose(RotationMath.rotationMatrix(pose)))
        }
    }

    @Test
    fun combinedRotations_roundTrip_acrossTheHeadsRange() {
        val poses = listOf(
            HeadPose(25f, -20f, 8f),
            HeadPose(-60f, 30f, -25f),
            HeadPose(75f, 10f, 45f), // steep yaw, still short of the |yaw| = 90° singularity
            HeadPose(-5f, -80f, 3f),
        )
        for (pose in poses) {
            assertPose(pose, RotationMath.toHeadPose(RotationMath.rotationMatrix(pose)))
        }
    }

    @Test
    fun positiveYaw_swingsTheFaceNormalToPlusX_theSubjectsLeft() {
        // The face looks along +Z; after yawing +30° that normal must gain a +X component.
        val m = RotationMath.rotationMatrix(HeadPose(30f, 0f, 0f))
        val normalX = RotationMath.at(m, 0, 2) // column 2 = the rotated +Z axis
        assertTrue(normalX > 0f, "expected +X component, got $normalX")
    }

    @Test
    fun positivePitch_tiltsTheFaceNormalDown() {
        val m = RotationMath.rotationMatrix(HeadPose(0f, 30f, 0f))
        val normalY = RotationMath.at(m, 1, 2)
        assertTrue(normalY < 0f, "expected -Y component, got $normalY")
    }

    @Test
    fun positiveRoll_tiltsTheCrownToMinusX_theSubjectsRight() {
        val m = RotationMath.rotationMatrix(HeadPose(0f, 0f, 30f))
        val crownX = RotationMath.at(m, 0, 1) // column 1 = the rotated +Y axis (crown of the head)
        assertTrue(crownX < 0f, "expected -X component, got $crownX")
    }

    @Test
    fun translationIsIgnored() {
        val m = RotationMath.rotationMatrix(HeadPose(20f, 5f, -10f))
        m[12] = 0.3f
        m[13] = -0.1f
        m[14] = -0.5f // MediaPipe places the face away from the camera
        assertPose(HeadPose(20f, 5f, -10f), RotationMath.toHeadPose(m))
    }

    @Test
    fun rejectsNonFourByFourInput() {
        assertFailsWith<IllegalArgumentException> { RotationMath.toHeadPose(FloatArray(9)) }
    }

    @Test
    fun unrotateAboutZ_removesAPureSensorRoll() {
        // A face reported with a 90° sensor rotation baked in reads as roll 94°; un-rotating by 90° → 4°.
        val reported = RotationMath.rotationMatrix(HeadPose(0f, 0f, 94f))
        assertPose(HeadPose(0f, 0f, 4f), RotationMath.toHeadPose(RotationMath.unrotateAboutZ(reported, 90f)))
    }

    @Test
    fun unrotateAboutZ_recoversTheTruePose_forACombinedRotation() {
        // Build "reported" = Rz(θ) · R_true exactly the way the leak happens, then undo it.
        val truePose = HeadPose(yaw = 20f, pitch = -12f, roll = 5f)
        // unrotateAboutZ(m, −θ) is Rz(+θ)·m — the leak itself.
        val reported = RotationMath.unrotateAboutZ(RotationMath.rotationMatrix(truePose), degrees = -270f)
        assertPose(truePose, RotationMath.toHeadPose(RotationMath.unrotateAboutZ(reported, degrees = 270f)))
    }

    @Test
    fun multiply_composesRotations_inApplicationOrder() {
        // Rz(30)·Ry(20)·Rx(10) built by parts must equal rotationMatrix(yaw 20, pitch 10, roll 30).
        val rz = RotationMath.rotationMatrix(HeadPose(0f, 0f, 30f))
        val ry = RotationMath.rotationMatrix(HeadPose(20f, 0f, 0f))
        val rx = RotationMath.rotationMatrix(HeadPose(0f, 10f, 0f))
        val composed = RotationMath.multiply(rz, RotationMath.multiply(ry, rx))
        assertPose(HeadPose(20f, 10f, 30f), RotationMath.toHeadPose(composed))
        // Identity is neutral and translation flows through the left factor.
        val t = FloatArray(16).also {
            it[0] = 1f
            it[5] = 1f
            it[10] = 1f
            it[15] = 1f
            it[13] = 1.35f
        }
        val out = RotationMath.multiply(t, ry)
        assertEquals(1.35f, out[13], eps)
        assertPose(HeadPose(20f, 0f, 0f), RotationMath.toHeadPose(out))
    }

    @Test
    fun unrotateAboutZ_byZero_isACopy() {
        val m = RotationMath.rotationMatrix(HeadPose(10f, 20f, 30f))
        val out = RotationMath.unrotateAboutZ(m, 0f)
        assertTrue(out contentEquals m)
        assertTrue(out !== m)
    }
}
