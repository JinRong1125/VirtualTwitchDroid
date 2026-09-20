package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BodyLeanTest {

    @Test
    fun neutral_isNoLean() {
        val lean = BodyLeanMapper.map(HeadOffset.ZERO)
        assertEquals(0f, lean.torso.roll, EPS) // (−0.0 from the sign flip is still no lean)
        assertEquals(0f, lean.torso.pitch, EPS)
        assertEquals(0f, lean.shiftX, EPS)
        assertEquals(0f, lean.shiftY, EPS)
    }

    @Test
    fun movingToTheSubjectsLeft_leansAndShiftsThatWay() {
        val lean = BodyLeanMapper.map(HeadOffset(x = 10f, y = 0f, z = 0f))
        // +X = subject's left; positive roll is crown-to-the-right, so leaning left is a NEGATIVE roll.
        assertEquals(-12f, lean.torso.roll, EPS)
        assertEquals(0f, lean.torso.pitch, EPS)
        assertEquals(0f, lean.torso.yaw, EPS)
        assertEquals(0.10f, lean.shiftX, EPS) // 10 cm → 0.10 m
        assertEquals(0f, lean.shiftY, EPS)
    }

    @Test
    fun movingTowardTheCamera_leansForward() {
        val lean = BodyLeanMapper.map(HeadOffset(x = 0f, y = 0f, z = 10f))
        assertEquals(6f, lean.torso.pitch, EPS) // positive pitch = face/torso forward-down
        assertEquals(0f, lean.torso.roll, EPS)
    }

    @Test
    fun bob_isDamped() {
        val lean = BodyLeanMapper.map(HeadOffset(x = 0f, y = 4f, z = 0f))
        assertEquals(0.02f, lean.shiftY, EPS) // 4 cm × 0.5
    }

    @Test
    fun everythingIsClamped() {
        val lean = BodyLeanMapper.map(HeadOffset(x = -100f, y = 100f, z = 100f))
        assertEquals(BodyLeanMapper.MAX_ROLL_DEG, lean.torso.roll, EPS)
        assertEquals(BodyLeanMapper.MAX_PITCH_DEG, lean.torso.pitch, EPS)
        assertEquals(-BodyLeanMapper.MAX_SHIFT_M, lean.shiftX, EPS)
        assertTrue(lean.shiftY <= BodyLeanMapper.MAX_SHIFT_M)
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
