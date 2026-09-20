package com.example.virtualtwitchdroid.feature.avatar.spring

import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import kotlin.math.sqrt
import kotlin.test.assertEquals
import org.junit.Test

class SpringMathTest {

    private fun assertVec(expected: FloatArray, actual: FloatArray, eps: Float = 1e-4f) {
        for (i in 0 until 3) assertEquals(expected[i], actual[i], eps, "component $i of ${actual.toList()}")
    }

    /** Hand-computed: a 90° yaw about +Y maps +X → −Z and +Z → +X (right-handed, R = Rz·Ry·Rx). */
    @Test
    fun transformDir_matchesAHandComputedRotation() {
        val yaw90 = RotationMath.rotationMatrix(HeadPose(yaw = 90f, pitch = 0f, roll = 0f))
        assertVec(SpringMath.vec(0f, 0f, -1f), SpringMath.transformDir(yaw90, SpringMath.vec(1f, 0f, 0f)))
        assertVec(SpringMath.vec(1f, 0f, 0f), SpringMath.transformDir(yaw90, SpringMath.vec(0f, 0f, 1f)))
    }

    @Test
    fun affineInverse_undoesRotationScaleAndTranslation() {
        val m = SpringMath.compose(
            translation = SpringMath.vec(0.3f, 0.9f, -0.1f),
            rotation = RotationMath.rotationMatrix(HeadPose(yaw = 25f, pitch = -10f, roll = 40f)),
            scale = SpringMath.vec(1.0125f, 1.0125f, 1.0125f),
        )
        val p = SpringMath.vec(-0.2f, 1.4f, 0.05f)
        val roundTrip = SpringMath.transformPoint(m, SpringMath.transformPoint(SpringMath.affineInverse(m), p))
        assertVec(p, roundTrip, 1e-5f)
        // Column-major identity from M · M⁻¹.
        val identity = SpringMath.multiply(m, SpringMath.affineInverse(m))
        for (i in 0 until 16) assertEquals(if (i % 5 == 0) 1f else 0f, identity[i], 1e-5f, "identity[$i]")
    }

    @Test
    fun fromToRotation_takesFromOntoTo_forGeneralAndAntiparallelVectors() {
        val from = SpringMath.normalize(SpringMath.vec(1f, 1f, 0f))
        val to = SpringMath.normalize(SpringMath.vec(0f, 1f, 1f))
        assertVec(to, SpringMath.transformDir(SpringMath.fromToRotation(from, to), from))
        // Hand-computed: +X onto +Y is a 90° turn about +Z, which sends +Y to −X.
        val quarter = SpringMath.fromToRotation(SpringMath.vec(1f, 0f, 0f), SpringMath.vec(0f, 1f, 0f))
        assertVec(SpringMath.vec(-1f, 0f, 0f), SpringMath.transformDir(quarter, SpringMath.vec(0f, 1f, 0f)))
        // Antiparallel: any 180° turn is fine as long as it lands on `to` and stays a rotation.
        val flip = SpringMath.fromToRotation(SpringMath.vec(0f, 1f, 0f), SpringMath.vec(0f, -1f, 0f))
        assertVec(SpringMath.vec(0f, -1f, 0f), SpringMath.transformDir(flip, SpringMath.vec(0f, 1f, 0f)))
        assertEquals(1f, SpringMath.length(SpringMath.transformDir(flip, SpringMath.vec(1f, 0f, 0f))), 1e-5f)
        // Parallel: identity.
        val same = SpringMath.fromToRotation(SpringMath.vec(0f, 0f, 1f), SpringMath.vec(0f, 0f, 1f))
        assertVec(SpringMath.vec(0.6f, 0.8f, 0f), SpringMath.transformDir(same, SpringMath.vec(0.6f, 0.8f, 0f)))
    }

    @Test
    fun rotationOnly_stripsScale_andTransposeInvertsIt() {
        val r = RotationMath.rotationMatrix(HeadPose(yaw = 30f, pitch = 20f, roll = -15f))
        val scaled = SpringMath.compose(SpringMath.vec(1f, 2f, 3f), r, SpringMath.vec(2f, 2f, 2f))
        val back = SpringMath.rotationOnly(scaled)
        for (i in 0 until 12) assertEquals(r[i], back[i], 1e-5f)
        val shouldBeIdentity = SpringMath.multiply(SpringMath.transposeRotation(back), back)
        for (i in 0 until 16) assertEquals(if (i % 5 == 0) 1f else 0f, shouldBeIdentity[i], 1e-5f)
        assertVec(SpringMath.vec(2f, 2f, 2f), SpringMath.scaleOf(scaled))
        assertEquals(sqrt(3f), SpringMath.length(SpringMath.vec(1f, 1f, 1f)), 1e-6f)
    }
}
