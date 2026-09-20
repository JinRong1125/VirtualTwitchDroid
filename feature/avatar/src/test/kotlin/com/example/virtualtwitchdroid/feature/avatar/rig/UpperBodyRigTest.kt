package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class UpperBodyRigTest {

    private val tracked = HeadPose(yaw = 30f, pitch = -12f, roll = 8f)

    private fun UpperBodyPose.sum(): HeadPose = rotations.values.fold(HeadPose.IDENTITY) { acc, p ->
        HeadPose(acc.yaw + p.yaw, acc.pitch + p.pitch, acc.roll + p.roll)
    }

    private fun assertPose(expected: HeadPose, actual: HeadPose) {
        assertEquals(expected.yaw, actual.yaw, EPS)
        assertEquals(expected.pitch, actual.pitch, EPS)
        assertEquals(expected.roll, actual.roll, EPS)
    }

    @Test
    fun shareTable_torsoTurnsMoreThanItLeans_andNodsLeast_headKeepsAPositiveShare() {
        val torso = UpperBodyRig.torsoShare
        assertTrue(torso.yaw >= torso.roll && torso.roll >= torso.pitch, "expected yaw ≥ roll ≥ pitch, got $torso")
        // Below 1 on every axis, or the head would counter-rotate against the body.
        assertTrue(torso.yaw < 1f && torso.pitch < 1f && torso.roll < 1f, "head share must stay positive: $torso")
        // The documented totals — change the KDoc if you retune the table.
        assertPose(HeadPose(yaw = 0.55f, pitch = 0.45f, roll = 0.50f), torso)
    }

    @Test
    fun chainPlusHead_equalsTheTrackedPose_andBonesGetTheirShare() {
        val pose = UpperBodyRig.distribute(tracked)
        assertPose(tracked, pose.sum())
        // Neck 0.25 / chest 0.12 of the yaw (what the device log ratios were checked against).
        assertEquals(tracked.yaw * 0.25f, pose[UpperBodyBone.NECK].yaw, EPS)
        assertEquals(tracked.yaw * 0.12f, pose[UpperBodyBone.CHEST].yaw, EPS)
        assertTrue(pose[UpperBodyBone.HEAD].yaw > pose[UpperBodyBone.NECK].yaw)
    }

    @Test
    fun missingUpperChest_foldsItsShareIntoChest_sameTotal() {
        val all = UpperBodyRig.distribute(tracked)
        val without = UpperBodyRig.distribute(
            tracked,
            present =
            UpperBodyBone.entries.toSet() - UpperBodyBone.UPPER_CHEST,
        )
        assertPose(tracked, without.sum())
        assertEquals(HeadPose.IDENTITY, without[UpperBodyBone.UPPER_CHEST])
        assertEquals(
            all[UpperBodyBone.CHEST].yaw + all[UpperBodyBone.UPPER_CHEST].yaw,
            without[UpperBodyBone.CHEST].yaw,
            EPS,
        )
        assertPose(all[UpperBodyBone.HEAD], without[UpperBodyBone.HEAD])
    }

    @Test
    fun missingNeck_passesItsShareToTheChest_headStillLandsOnTracked() {
        // A VRM without `neck` (optional in the spec): the driven bones must still add up to the tracked
        // pose — a share subtracted from the head but applied nowhere would make the face undershoot.
        val present = setOf(UpperBodyBone.SPINE, UpperBodyBone.CHEST, UpperBodyBone.UPPER_CHEST, UpperBodyBone.HEAD)
        val pose = UpperBodyRig.distribute(tracked, present = present)
        assertPose(tracked, pose.sum())
        assertEquals(HeadPose.IDENTITY, pose[UpperBodyBone.NECK])
        assertEquals(tracked.yaw * (0.10f + 0.25f), pose[UpperBodyBone.UPPER_CHEST].yaw, EPS)
    }

    @Test
    fun lean_goesToTheTorso_andTheHeadCounterRotates_soTheFaceStaysTracked() {
        val lean = HeadPose(yaw = 0f, pitch = 6f, roll = -12f)
        val pose = UpperBodyRig.distribute(tracked, lean = lean)
        assertPose(tracked, pose.sum()) // chain + head still equals the tracked orientation
        // Lean shares: spine 0.5, chest 0.3, upperChest 0.2 on top of the follow-through shares.
        val plain = UpperBodyRig.distribute(tracked)
        assertEquals(plain[UpperBodyBone.SPINE].roll + lean.roll * 0.5f, pose[UpperBodyBone.SPINE].roll, EPS)
        assertEquals(plain[UpperBodyBone.CHEST].roll + lean.roll * 0.3f, pose[UpperBodyBone.CHEST].roll, EPS)
        assertEquals(
            plain[UpperBodyBone.UPPER_CHEST].pitch + lean.pitch * 0.2f,
            pose[UpperBodyBone.UPPER_CHEST].pitch,
            EPS,
        )
        assertEquals(plain[UpperBodyBone.NECK], pose[UpperBodyBone.NECK]) // the neck does not lean
        assertEquals(plain[UpperBodyBone.HEAD].roll - lean.roll, pose[UpperBodyBone.HEAD].roll, EPS)
    }

    @Test
    fun lean_withNoTorsoBone_isDropped_notPutOnTheHead() {
        val pose = UpperBodyRig.distribute(tracked, present = setOf(UpperBodyBone.HEAD), lean = HeadPose(0f, 6f, -12f))
        assertPose(tracked, pose[UpperBodyBone.HEAD])
    }

    @Test
    fun headOnlyModel_headCarriesEverything() {
        val pose = UpperBodyRig.distribute(tracked, present = setOf(UpperBodyBone.HEAD))
        assertEquals(1, pose.rotations.size)
        assertPose(tracked, pose[UpperBodyBone.HEAD])
    }

    @Test
    fun laggedTorso_leavesTheFaceOnTheTrackedPose() {
        // Torso still at rest (lag) while the head already turned: the head bone carries everything.
        val pose = UpperBodyRig.distribute(tracked, followed = HeadPose.IDENTITY)
        assertPose(tracked, pose[UpperBodyBone.HEAD])
        assertEquals(HeadPose.IDENTITY, pose[UpperBodyBone.CHEST])
        assertPose(tracked, pose.sum())
    }

    @Test
    fun neutralHead_isNeutralEverywhere() {
        UpperBodyRig.distribute(HeadPose.IDENTITY).rotations.values.forEach { assertPose(HeadPose.IDENTITY, it) }
    }

    @Test
    fun follow_lagsByTimeNotByFrame_thenConverges() {
        val tau = 150_000_000L // 150 ms
        val follow = UpperBodyFollow(timeConstantNanos = tau)
        assertEquals(HeadPose.IDENTITY, follow.next(HeadPose.IDENTITY, nowNanos = 0L)) // first sample passes through

        // One time constant later the torso has covered ~63 % of the step, whatever the frame rate…
        val afterTau = follow.next(tracked, nowNanos = tau)
        assertEquals(tracked.yaw * 0.632f, afterTau.yaw, 0.05f)
        // …and it converges (e^-21 of the step is left after 21 τ — far below EPS).
        var p = afterTau
        repeat(20) { p = follow.next(tracked, nowNanos = tau * (2 + it)) }
        assertPose(tracked, p)

        // Same elapsed time in many small frames lands at the same place as one big frame.
        val fine = UpperBodyFollow(tau)
        fine.next(HeadPose.IDENTITY, 0L)
        var q = HeadPose.IDENTITY
        for (i in 1..30) q = fine.next(tracked, tau * i / 30)
        assertEquals(afterTau.yaw, q.yaw, 0.05f)

        follow.reset()
        assertEquals(tracked, follow.next(tracked, 0L))
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
