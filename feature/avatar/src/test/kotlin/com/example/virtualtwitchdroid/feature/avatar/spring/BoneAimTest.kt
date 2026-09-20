package com.example.virtualtwitchdroid.feature.avatar.spring

import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

/** Aiming under a rotated + scaled parent with rotated rest locals (the Seed-san arm shape), no Filament. */
class BoneAimTest {

    private class Tree : BoneTransforms {
        val parent = HashMap<Int, Int>()
        val local = HashMap<Int, FloatArray>()
        val rest = HashMap<Int, FloatArray>()

        fun node(id: Int, parentId: Int?, local: FloatArray) {
            parentId?.let { parent[id] = it }
            this.local[id] = local
            rest[id] = local.copyOf()
        }

        override fun world(node: Int): FloatArray? {
            val l = local[node] ?: return null
            return parent[node]?.let { SpringMath.multiply(world(it)!!, l) } ?: l
        }

        override fun parentWorld(node: Int): FloatArray? = parent[node]?.let { world(it) } ?: SpringMath.identity()

        override fun restLocal(node: Int): FloatArray? = rest[node]

        override fun setLocal(node: Int, local: FloatArray) {
            this.local[node] = local
        }
    }

    private fun trs(x: Float, y: Float, z: Float, pose: HeadPose = HeadPose.IDENTITY, s: Float = 1f) =
        SpringMath.compose(SpringMath.vec(x, y, z), RotationMath.rotationMatrix(pose), SpringMath.vec(s, s, s))

    /** chest (rotated, scaled) → shoulder → upper arm (rest: bone along local +Y, Blender-style) → lower arm → hand. */
    private fun arm(): Tree = Tree().apply {
        node(0, null, trs(0f, 1.2f, 0f, HeadPose(yaw = 25f, pitch = 5f, roll = -8f), s = 1.0125f))
        node(1, 0, trs(0.04f, 0.2f, 0.03f, HeadPose(0f, 0f, -60f)))
        node(2, 1, trs(0f, 0.1f, 0f, HeadPose(0f, 0f, 30f)))
        node(3, 2, trs(0f, 0.25f, 0f, HeadPose(10f, 0f, 0f)))
        node(4, 3, trs(0f, 0.16f, 0f))
    }

    private fun assertDir(expected: FloatArray, actual: FloatArray, eps: Float = 1e-4f) {
        for (i in 0 until 3) assertEquals(expected[i], actual[i], eps, "component $i of ${actual.toList()}")
    }

    @Test
    fun aimLocal_pointsTheBoneAlongTheWorldTarget_andKeepsTranslationAndLength() {
        val t = arm()
        val before = SpringMath.length(
            SpringMath.sub(SpringMath.translation(t.world(3)!!), SpringMath.translation(t.world(2)!!)),
        )
        val target = SpringMath.normalize(SpringMath.vec(0.2f, -0.97f, 0.1f))
        val local = assertNotNull(BoneAim.aimLocal(t, node = 2, childNode = 3, targetDirection = target))
        t.setLocal(2, local)
        assertDir(target, assertNotNull(BoneAim.currentDirection(t, 2, 3)))
        // The joint itself did not move and the bone kept its length (rotation only).
        assertDir(SpringMath.translation(t.rest[2]!!), SpringMath.translation(local))
        val after = SpringMath.length(
            SpringMath.sub(SpringMath.translation(t.world(3)!!), SpringMath.translation(t.world(2)!!)),
        )
        assertEquals(before, after, 1e-4f)
    }

    @Test
    fun aimingTheChainTopDown_worksForTheChildToo() {
        val t = arm()
        t.setLocal(2, assertNotNull(BoneAim.aimLocal(t, 2, 3, SpringMath.vec(0f, -1f, 0f))))
        val lowerTarget = SpringMath.normalize(SpringMath.vec(0.1f, -0.9f, 0.4f))
        t.setLocal(3, assertNotNull(BoneAim.aimLocal(t, 3, 4, lowerTarget)))
        assertDir(SpringMath.vec(0f, -1f, 0f), assertNotNull(BoneAim.currentDirection(t, 2, 3)))
        assertDir(lowerTarget, assertNotNull(BoneAim.currentDirection(t, 3, 4)))
    }

    @Test
    fun rotateInWorld_turnsAboutAWorldAxis_whateverTheParentOrientation() {
        val t = arm()
        val down = SpringMath.vec(0f, -1f, 0f)
        val base = assertNotNull(BoneAim.aimLocal(t, 2, 3, down))
        t.setLocal(2, base)
        // Lift 30° about world Z: a bone pointing −Y swings toward +X (right-handed rotation about +Z).
        val lift = RotationMath.rotationMatrix(HeadPose(yaw = 0f, pitch = 0f, roll = 30f))
        t.setLocal(2, assertNotNull(BoneAim.rotateInWorld(t, 2, base, lift)))
        val expected = SpringMath.transformDir(lift, down)
        assertDir(expected, assertNotNull(BoneAim.currentDirection(t, 2, 3)))
        assertEquals(0.5f, expected[0], 1e-4f) // sin 30°
    }

    @Test
    fun rotateInWorld_onTheMinusXSide_aNegativeAngleAlsoLiftsOutward() {
        // The renderer rotates every arm bone by `outward × angle` about world +Z: on the −X side that is a
        // NEGATIVE angle, and it must swing a hanging bone toward −X (outward for that side), i.e. up.
        val t = arm()
        val down = SpringMath.vec(0f, -1f, 0f)
        val base = assertNotNull(BoneAim.aimLocal(t, 2, 3, down))
        t.setLocal(2, base)
        val outward = -1f
        val lift = RotationMath.rotationMatrix(HeadPose(yaw = 0f, pitch = 0f, roll = outward * 30f))
        t.setLocal(2, assertNotNull(BoneAim.rotateInWorld(t, 2, base, lift)))
        val dir = assertNotNull(BoneAim.currentDirection(t, 2, 3))
        assertEquals(-0.5f, dir[0], 1e-4f) // toward −X …
        assertEquals(-0.866f, dir[1], 1e-3f) // … and up from straight down
    }
}
