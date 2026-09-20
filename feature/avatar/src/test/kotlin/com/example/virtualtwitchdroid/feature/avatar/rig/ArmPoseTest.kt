package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ArmPoseTest {

    private fun length(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    @Test
    fun relaxedArms_hangDown_slightlyOutward_elbowsForward_mirroredPerSide() {
        val left = ArmRestPose.upperArmDirection(outward = 1f)
        val right = ArmRestPose.upperArmDirection(outward = -1f)
        assertEquals(1f, length(left), 1e-5f)
        assertTrue(left[1] < -0.9f, "the upper arm must hang down: ${left.toList()}")
        assertTrue(left[0] > 0f && right[0] < 0f, "outward is +X on the +X side and −X on the other")
        assertEquals(left[0], -right[0], 1e-6f)
        assertEquals(left[1], right[1], 1e-6f)
        assertEquals(0f, left[2], 1e-6f) // upper arm has no forward tilt

        val lower = ArmRestPose.lowerArmDirection(outward = 1f)
        assertEquals(1f, length(lower), 1e-5f)
        assertTrue(lower[2] > 0.2f, "the forearm bends forward (+Z, toward the camera): ${lower.toList()}")
        assertTrue(lower[1] < -0.9f)
        assertTrue(abs(lower[0]) < left[0], "the forearm comes back in toward the body")
    }

    @Test
    fun idleMotion_isPeriodic_boundedAndZeroMean() {
        val samples = (0 until 400).map { IdleMotion.at(it * IdleMotion.BREATH_PERIOD_S / 100f) }
        assertTrue(samples.all { it.breath in 0f..1f })
        assertTrue(samples.all { abs(it.chestPitchDeg) <= IdleMotion.BREATH_CHEST_PITCH_DEG + 1e-5f })
        assertTrue(samples.all { abs(it.shoulderLiftDeg) <= IdleMotion.BREATH_SHOULDER_LIFT_DEG + 1e-5f })
        assertTrue(samples.all { abs(it.armSwayDeg) <= IdleMotion.ARM_SWAY_DEG + 1e-5f })
        // Zero-mean over whole periods (4 breaths); the sway period differs but stays small in the mean.
        val meanLift = samples.map { it.shoulderLiftDeg }.average()
        assertEquals(0.0, meanLift, 1e-3)
        // Periodic: one breath later, the same values.
        val a = IdleMotion.at(1.3f)
        val b = IdleMotion.at(1.3f + IdleMotion.BREATH_PERIOD_S)
        assertEquals(a.breath, b.breath, 1e-5f)
        assertEquals(a.shoulderLiftDeg, b.shoulderLiftDeg, 1e-5f)
        // Exhaled at t = 0, inhaled half a period later, shoulders up on the inhale, chest opening (negative pitch).
        assertEquals(0f, IdleMotion.at(0f).breath, 1e-6f)
        val inhaled = IdleMotion.at(IdleMotion.BREATH_PERIOD_S / 2f)
        assertEquals(1f, inhaled.breath, 1e-5f)
        assertTrue(inhaled.shoulderLiftDeg > 0f && inhaled.chestPitchDeg < 0f)
    }
}
