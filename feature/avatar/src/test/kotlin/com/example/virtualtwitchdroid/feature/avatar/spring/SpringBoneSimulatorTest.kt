package com.example.virtualtwitchdroid.feature.avatar.spring

import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringBoneSet
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringChain
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringCollider
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringJoint
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The spring simulation on a tiny scene graph (no Filament): a joint at node 1 (parent node 0) with a
 * horizontal 10 cm bone to its tip at node 2 (`rest = +X`).
 */
class SpringBoneSimulatorTest {

    /** A minimal bone tree: locals are stored, worlds are `parentWorld · local`, rests are the initial locals. */
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

    private fun translation(x: Float, y: Float, z: Float) = SpringMath.identity().also {
        it[12] = x
        it[13] = y
        it[14] = z
    }

    private fun tree(rootLocal: FloatArray = translation(0f, 1f, 0f)): Tree = Tree().apply {
        node(0, null, rootLocal) // the "head" the hair hangs from
        node(1, 0, translation(0f, 0f, 0f)) // joint
        node(2, 1, translation(LENGTH, 0f, 0f)) // tip: bone points +X at rest
    }

    private fun set(joint: SpringJoint, colliders: List<SpringCollider> = emptyList(), center: Int? = null) =
        SpringBoneSet(
            colliders,
            listOf(SpringChain("hair", listOf(joint, joint.copy(node = 2)), colliders.indices.toList(), center)),
        )

    private fun tipWorld(t: Tree) = SpringMath.translation(t.world(2)!!)

    private fun jointWorld(t: Tree) = SpringMath.translation(t.world(1)!!)

    private fun assertLengthKept(t: Tree) {
        assertEquals(LENGTH, SpringMath.length(SpringMath.sub(tipWorld(t), jointWorld(t))), 1e-4f)
    }

    @Test
    fun gravityOnly_tailHangsDown_lengthPreserved() {
        val t = tree()
        val sim =
            SpringBoneSimulator(set(SpringJoint(node = 1, stiffness = 0f, gravityPower = 1f, dragForce = 0.3f)), t)
        assertEquals(1, sim.segmentCount)
        repeat(300) { sim.step(DT, t) }
        val tip = tipWorld(t)
        assertEquals(0f, tip[0], 0.01f) // no longer pointing +X…
        assertEquals(1f - LENGTH, tip[1], 0.01f) // …but straight down from the joint at y = 1
        assertLengthKept(t)
    }

    @Test
    fun stiffness_pullsBackToRest_afterTheParentTurns() {
        // Start with the parent rolled 90° so the rest direction is world −Y… then snap the parent back.
        val rolled = SpringMath.multiply(translation(0f, 1f, 0f), RotationMath.rotationMatrix(HeadPose(0f, 0f, -90f)))
        val t = tree(rootLocal = rolled)
        val sim = SpringBoneSimulator(set(SpringJoint(node = 1, stiffness = 4f, gravityPower = 0f, dragForce = 1f)), t)
        t.local[0] = translation(0f, 1f, 0f) // the head turns back instantly; the hair must catch up
        sim.step(DT, t)
        val afterOne = SpringMath.normalize(SpringMath.sub(tipWorld(t), jointWorld(t)))
        assertTrue(afterOne[0] < 0.99f, "hair should still lag right after the turn, got x=${afterOne[0]}")
        assertTrue(sim.lastMaxTailTravel > 0f)
        repeat(120) { sim.step(DT, t) }
        val settled = SpringMath.normalize(SpringMath.sub(tipWorld(t), jointWorld(t)))
        assertEquals(1f, settled[0], 0.02f) // back on the rest direction (+X in world)
        assertLengthKept(t)
    }

    @Test
    fun sphereCollider_keepsTheTailOutside() {
        val t = tree()
        t.node(3, null, translation(LENGTH, 1f - LENGTH, 0f)) // a sphere right where gravity wants the tail
        val collider = SpringCollider(node = 3, offset = floatArrayOf(0f, 0f, 0f), radius = 0.05f)
        val sim =
            SpringBoneSimulator(
                set(
                    SpringJoint(node = 1, stiffness = 0f, gravityPower = 1f, dragForce = 0.3f, hitRadius = 0.01f),
                    listOf(collider),
                ),
                t,
            )
        repeat(300) { sim.step(DT, t) }
        val gap = SpringMath.length(SpringMath.sub(tipWorld(t), floatArrayOf(LENGTH, 1f - LENGTH, 0f)))
        assertTrue(gap >= 0.06f - 1e-3f, "tail penetrated the collider: distance $gap")
        assertLengthKept(t)
    }

    @Test
    fun zeroLengthSegment_isSkipped() {
        val t = Tree().apply {
            node(0, null, translation(0f, 1f, 0f))
            node(1, 0, translation(0f, 0f, 0f))
            node(2, 1, translation(0f, 0f, 0f)) // tip helper on top of the joint
        }
        assertEquals(0, SpringBoneSimulator(set(SpringJoint(node = 1)), t).segmentCount)
    }

    @Test
    fun centerNode_movingTheWholeModel_doesNotSwingTheHair() {
        val t = tree()
        val sim =
            SpringBoneSimulator(
                set(SpringJoint(node = 1, stiffness = 0f, gravityPower = 0f, dragForce = 0f), center = 0),
                t,
            )
        t.local[0] = translation(1f, 1f, 0f) // the whole model (the center) slides 1 m sideways
        repeat(5) { sim.step(DT, t) }
        val dir = SpringMath.normalize(SpringMath.sub(tipWorld(t), jointWorld(t)))
        assertEquals(1f, dir[0], 1e-3f) // still pointing +X: no inertia from the model's own motion
        assertTrue(abs(dir[1]) < 1e-3f)
    }

    @Test
    fun withoutCenter_movingTheModel_doesSwingTheHair() {
        val t = tree()
        val sim = SpringBoneSimulator(set(SpringJoint(node = 1, stiffness = 0f, gravityPower = 0f, dragForce = 0f)), t)
        t.local[0] = translation(0f, 1.05f, 0f) // the head jumps up 5 cm; the tail is left behind → bone tilts down
        sim.step(DT, t)
        val dir = SpringMath.normalize(SpringMath.sub(tipWorld(t), jointWorld(t)))
        assertTrue(dir[1] < -0.1f, "expected the hair to lag below the horizontal, got $dir")
        assertLengthKept(t)
    }

    /**
     * The production shape: a scaled + rotated root, two segments whose joints have rotated rest locals,
     * and a parent that is STILL rotated when we assert — so the `Q · restRot` / `parentRotᵀ · world`
     * composition is exercised, not just translations. Expected coordinates are computed by hand.
     */
    @Test
    fun rotatedRestsUnderARotatedScaledParent_zeroForces_boneStaysPut_and_hangsDownUnderGravity() {
        val s = 1.0125f
        // Root: yaw 90° (so its +X points to world −Z) scaled by s, at y = 1.
        val root = SpringMath.compose(
            SpringMath.vec(0f, 1f, 0f),
            RotationMath.rotationMatrix(HeadPose(90f, 0f, 0f)),
            floatArrayOf(s, s, s),
        )
        // Joint 1 sits 0.1 along the root's +X (world −Z) and is rolled 30°; its child sits 0.1 along ITS +X.
        val j1 = SpringMath.compose(
            SpringMath.vec(0.1f, 0f, 0f),
            RotationMath.rotationMatrix(HeadPose(0f, 0f, 30f)),
            floatArrayOf(1f, 1f, 1f),
        )
        val j2 = SpringMath.compose(
            SpringMath.vec(0.1f, 0f, 0f),
            RotationMath.rotationMatrix(HeadPose(0f, 20f, 0f)),
            floatArrayOf(1f, 1f, 1f),
        )
        val tip = translation(0.1f, 0f, 0f)
        val t = Tree().apply {
            node(0, null, root)
            node(1, 0, j1)
            node(2, 1, j2)
            node(3, 2, tip)
        }
        val restTip = tipOf(t, 3)
        val restMid = tipOf(t, 2)

        // Zero forces: nothing may drift, whatever the frames look like.
        val still = SpringBoneSimulator(
            SpringBoneSet(
                emptyList(),
                listOf(
                    SpringChain(
                        "c",
                        listOf(joint(1, 0f, 0f, 1f), joint(2, 0f, 0f, 1f), joint(3, 0f, 0f, 1f)),
                        emptyList(),
                        null,
                    ),
                ),
            ),
            t,
        )
        assertEquals(2, still.segmentCount)
        repeat(30) { still.step(DT, t) }
        assertPoint(restMid, tipOf(t, 2))
        assertPoint(restTip, tipOf(t, 3))

        // Gravity: both segments hang straight down from joint 1's world position, lengths kept in world scale.
        val head = tipOf(t, 1)
        val hang = SpringBoneSimulator(
            SpringBoneSet(
                emptyList(),
                listOf(
                    SpringChain(
                        "c",
                        listOf(joint(1, 0f, 1f, 0.3f), joint(2, 0f, 1f, 0.3f), joint(3, 0f, 1f, 0.3f)),
                        emptyList(),
                        null,
                    ),
                ),
            ),
            t,
        )
        repeat(400) { hang.step(DT, t) }
        assertPoint(floatArrayOf(head[0], head[1] - 0.1f * s, head[2]), tipOf(t, 2), 0.003f)
        assertPoint(floatArrayOf(head[0], head[1] - 0.2f * s, head[2]), tipOf(t, 3), 0.003f)
    }

    /**
     * The virtual tail under the same rotated + scaled parent with rotated rest locals: the leaf's bone axis
     * is derived through `affineInverse(jointWorld)`, so an inverse/transpose/scale mistake shows up here
     * (zero forces must hold the rest pose exactly; gravity must hang the 7 cm tail straight down).
     */
    @Test
    fun leafTail_underARotatedScaledParent_staysPutWithoutForces_andHangsDownUnderGravity() {
        val s = 1.0125f
        val root = SpringMath.compose(
            SpringMath.vec(0f, 1f, 0f),
            RotationMath.rotationMatrix(HeadPose(90f, 0f, 0f)),
            floatArrayOf(s, s, s),
        )
        val j1 = SpringMath.compose(
            SpringMath.vec(0.1f, 0f, 0f),
            RotationMath.rotationMatrix(HeadPose(0f, 0f, 30f)),
            floatArrayOf(1f, 1f, 1f),
        )
        val leaf = SpringMath.compose(
            SpringMath.vec(0.1f, 0f, 0f),
            RotationMath.rotationMatrix(HeadPose(0f, 20f, 0f)),
            floatArrayOf(1f, 1f, 1f),
        )
        fun scene() = Tree().apply {
            node(0, null, root)
            node(1, 0, j1)
            node(2, 1, leaf) // the leaf: no child at all
        }
        fun chain(gravity: Float, drag: Float) = SpringBoneSet(
            emptyList(),
            listOf(
                SpringChain(
                    "c",
                    listOf(joint(1, 0f, gravity, drag), joint(2, 0f, gravity, drag)),
                    emptyList(),
                    null,
                    leafTail = true,
                ),
            ),
        )
        val still = scene()
        val restLeaf = still.local[2]!!.copyOf()
        val sim = SpringBoneSimulator(chain(gravity = 0f, drag = 1f), still)
        assertEquals(2, sim.segmentCount) // 1→2 plus the leaf's virtual tail
        repeat(30) { sim.step(DT, still) }
        for (i in 0 until 16) assertEquals(restLeaf[i], still.local[2]!![i], 1e-4f, "leaf local[$i] drifted")

        val hang = scene()
        // At rest the leaf's bone axis (toward its virtual tail) is the parent→leaf world direction; express
        // it in the leaf's own rest frame so we can ask where the simulator has turned it afterwards.
        val restLeafWorld = hang.world(2)!!.copyOf()
        val restAxisWorld = SpringMath.normalize(SpringMath.sub(SpringMath.translation(restLeafWorld), tipOf(hang, 1)))
        val axisLocal = SpringMath.normalize(
            SpringMath.transformDir(SpringMath.affineInverse(restLeafWorld), restAxisWorld),
        )
        val sim2 = SpringBoneSimulator(chain(gravity = 1f, drag = 0.3f), hang)
        repeat(400) { sim2.step(DT, hang) }
        // The leaf's joint hangs 0.1·s below joint 1 and its own virtual tail then points straight down too.
        val head1 = tipOf(hang, 1)
        assertPoint(floatArrayOf(head1[0], head1[1] - 0.1f * s, head1[2]), tipOf(hang, 2), 0.003f)
        val axisNow = SpringMath.normalize(SpringMath.transformDir(hang.world(2)!!, axisLocal))
        assertPoint(floatArrayOf(0f, -1f, 0f), axisNow, 0.01f)
    }

    @Test
    fun capsuleCollider_keepsTheTailOffItsAxis() {
        val t = tree()
        // A capsule lying along X, just below the rest position of the bone's tail.
        t.node(3, null, translation(0f, 1f - 0.05f, 0f))
        val capsule =
            SpringCollider(
                node = 3,
                offset = floatArrayOf(-0.2f, 0f, 0f),
                radius = 0.03f,
                tail = floatArrayOf(0.4f, 0f, 0f),
            )
        val sim =
            SpringBoneSimulator(
                set(
                    SpringJoint(node = 1, stiffness = 0f, gravityPower = 1f, dragForce = 0.3f, hitRadius = 0.01f),
                    listOf(capsule),
                ),
                t,
            )
        repeat(300) { sim.step(DT, t) }
        val tip = tipWorld(t)
        // Closest point on the capsule axis is directly below the tip; it must stay ≥ radius + hitRadius away.
        val gap = SpringMath.length(SpringMath.sub(tip, floatArrayOf(tip[0], 1f - 0.05f, 0f)))
        assertTrue(gap >= 0.04f - 1e-3f, "tail penetrated the capsule: gap $gap")
        assertLengthKept(t)
    }

    @Test
    fun leafTail_singleBoneChain_getsAVirtualTailSegment_thatHangsUnderGravity() {
        // An ear: one bone (node 1) sticking out of the head (node 0) along +X, no children at all.
        val t = Tree().apply {
            node(0, null, translation(0f, 1f, 0f))
            node(1, 0, translation(0.05f, 0f, 0f))
        }
        val joint = SpringJoint(node = 1, stiffness = 0f, gravityPower = 1f, dragForce = 0.3f)
        // Without the virtual tail there is nothing to simulate…
        assertEquals(
            0,
            SpringBoneSimulator(
                SpringBoneSet(emptyList(), listOf(SpringChain("ear", listOf(joint), emptyList(), null))),
                t,
            ).segmentCount,
        )
        // …with it, the leaf bone itself swings: its local +X (the parent→leaf direction at rest) ends up pointing down.
        val sim = SpringBoneSimulator(
            SpringBoneSet(emptyList(), listOf(SpringChain("ear", listOf(joint), emptyList(), null, leafTail = true))),
            t,
        )
        assertEquals(1, sim.segmentCount)
        repeat(300) { sim.step(DT, t) }
        val axis = SpringMath.normalize(SpringMath.transformDir(t.world(1)!!, floatArrayOf(1f, 0f, 0f)))
        assertEquals(-1f, axis[1], 0.02f)
        assertEquals(0.05f, SpringMath.translation(t.world(1)!!)[0], 1e-5f) // the joint itself never moves
    }

    @Test
    fun leafTail_onAMultiJointChain_addsOneSegmentForTheLastJoint() {
        val t = tree() // 0 → 1 → 2, bone 1→2 along +X; node 2 is the leaf now
        val joint = SpringJoint(node = 1, stiffness = 0f, gravityPower = 0f, dragForce = 0f)
        val chain = SpringChain("hair", listOf(joint, joint.copy(node = 2)), emptyList(), null, leafTail = true)
        val sim = SpringBoneSimulator(SpringBoneSet(emptyList(), listOf(chain)), t)
        assertEquals(2, sim.segmentCount)
        repeat(10) { sim.step(DT, t) } // no forces: the rest pose holds, including the leaf's virtual tail
        assertEquals(LENGTH, SpringMath.translation(t.world(2)!!)[0], 1e-5f)
        val leafAxis = SpringMath.transformDir(t.world(2)!!, floatArrayOf(1f, 0f, 0f))
        assertEquals(1f, leafAxis[0], 1e-4f)
    }

    @Test
    fun leafTail_leafOnTopOfItsParent_hasNoDirection_andIsSkipped() {
        val t = Tree().apply {
            node(0, null, translation(0f, 1f, 0f))
            node(1, 0, translation(0f, 0f, 0f))
        }
        val chain = SpringChain("dot", listOf(SpringJoint(node = 1)), emptyList(), null, leafTail = true)
        val sim = SpringBoneSimulator(SpringBoneSet(emptyList(), listOf(chain)), t)
        assertEquals(0, sim.segmentCount)
        assertEquals(listOf<String?>("dot"), sim.droppedChains)
    }

    private fun joint(node: Int, stiffness: Float, gravity: Float, drag: Float) =
        SpringJoint(node = node, stiffness = stiffness, gravityPower = gravity, dragForce = drag)

    private fun tipOf(t: Tree, node: Int) = SpringMath.translation(t.world(node)!!)

    private fun assertPoint(expected: FloatArray, actual: FloatArray, eps: Float = 1e-4f) {
        for (i in 0 until 3) {
            assertEquals(
                expected[i],
                actual[i],
                eps,
                "component $i: ${actual.toList()} vs ${expected.toList()}",
            )
        }
    }

    private companion object {
        const val LENGTH = 0.1f
        const val DT = 1f / 30f
    }
}
