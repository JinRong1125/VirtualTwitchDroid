package com.example.virtualtwitchdroid.feature.avatar.render

import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRig
import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtType
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmLookAt
import com.example.virtualtwitchdroid.feature.avatar.vrm.MorphBind
import com.example.virtualtwitchdroid.feature.avatar.vrm.VrmModel
import com.example.virtualtwitchdroid.feature.avatar.vrm.VrmVersion
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class MorphWeightsTest {

    private val model = VrmModel(
        version = VrmVersion.V1,
        title = null,
        humanoidBones = emptyMap(),
        expressions = mapOf(
            VrmExpression.AA to listOf(MorphBind(node = 2, index = 3, weight = 1f)),
            VrmExpression.BLINK to listOf(MorphBind(2, 0, 1f), MorphBind(2, 1, 1f)),
            VrmExpression.HAPPY to listOf(MorphBind(2, 3, 0.5f), MorphBind(7, 0, 1f)), // shares index 3 with AA
            VrmExpression.SAD to listOf(MorphBind(9, 0, 1f)), // node 9 has no morph mesh
        ),
        morphTargetCounts = mapOf(2 to 4, 7 to 1),
        nodeNames = emptyList(),
    )

    private fun rig(vararg weights: Pair<VrmExpression, Float>) = FaceRig(weights.toMap(), HeadPose.IDENTITY, true)

    @Test
    fun bindsScaleByBothTheRigAndTheBindWeight_andSharedTargetsAccumulate() {
        val out = MorphWeights.compose(rig(VrmExpression.AA to 0.5f, VrmExpression.HAPPY to 0.4f), model)
        // index 3: AA 0.5·1 + HAPPY 0.4·0.5 = 0.7; node 7 index 0: HAPPY 0.4·1.
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0.7f), out.getValue(2))
        assertContentEquals(floatArrayOf(0.4f), out.getValue(7))
    }

    @Test
    fun sharedTargets_clampAtOne() {
        val out = MorphWeights.compose(rig(VrmExpression.AA to 1f, VrmExpression.HAPPY to 1f), model)
        assertEquals(1f, out.getValue(2)[3])
    }

    @Test
    fun zeroWeights_andUnknownNodes_produceNothing() {
        assertTrue(MorphWeights.compose(rig(VrmExpression.AA to 0f), model).isEmpty())
        assertTrue(MorphWeights.compose(rig(VrmExpression.SAD to 1f), model).isEmpty())
        assertTrue(MorphWeights.compose(FaceRig.NEUTRAL, model).isEmpty())
    }

    @Test
    fun lookMorphs_areSkipped_whenTheGazeIsBoneDriven_andKeptOtherwise() {
        val withLook = model.copy(
            expressions = model.expressions + (VrmExpression.LOOK_LEFT to listOf(MorphBind(2, 2, 1f))),
        )
        val gaze = rig(VrmExpression.LOOK_LEFT to 1f, VrmExpression.AA to 0.5f)
        // Expression-driven (or unknown) gaze: the look morph is applied like any other.
        assertContentEquals(floatArrayOf(0f, 0f, 1f, 0.5f), MorphWeights.compose(gaze, withLook).getValue(2))
        val expression = VrmLookAt.defaultRange(LookAtType.EXPRESSION)
        val expressionModel = withLook.copy(
            lookAt = VrmLookAt(LookAtType.EXPRESSION, expression, expression, expression, expression),
        )
        assertContentEquals(floatArrayOf(0f, 0f, 1f, 0.5f), MorphWeights.compose(gaze, expressionModel).getValue(2))
        // Bone-driven gaze: the eye bones own it — the look morph must not fire on top (double gaze).
        val bone = VrmLookAt.defaultRange(LookAtType.BONE)
        val boneModel = withLook.copy(lookAt = VrmLookAt(LookAtType.BONE, bone, bone, bone, bone))
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0.5f), MorphWeights.compose(gaze, boneModel).getValue(2))
    }

    @Test
    fun blink_drivesBothEyeTargets() {
        assertContentEquals(
            floatArrayOf(0.8f, 0.8f, 0f, 0f),
            MorphWeights.compose(rig(VrmExpression.BLINK to 0.8f), model).getValue(2),
        )
    }

    /** The renderer's reusable buffers: one per driven node that has a morph-target count (2 and 7). */
    private fun freshScratch() = mapOf(2 to FloatArray(4), 7 to FloatArray(1))

    /**
     * The allocation-free [MorphWeights.composeInto] must fill each buffer with EXACTLY what the old
     * `compose(rig, model)[node] ?: zeros(count)` path produced — for every node, driven or not — so the
     * per-frame render allocation can be removed without changing a single morph weight.
     */
    @Test
    fun composeInto_matchesTheComposePlusResetPath_forEveryNode() {
        val rigs = listOf(
            FaceRig.NEUTRAL,
            rig(VrmExpression.AA to 0.5f, VrmExpression.HAPPY to 0.4f),
            rig(VrmExpression.AA to 1f, VrmExpression.HAPPY to 1f), // clamps at 1
            rig(VrmExpression.BLINK to 0.8f),
            rig(VrmExpression.SAD to 1f), // node 9 has no mesh → contributes nothing
            rig(VrmExpression.AA to 0f), // zero weight → nothing
        )
        for (r in rigs) {
            val scratch = freshScratch()
            MorphWeights.composeInto(r, model, scratch)
            val reference = MorphWeights.compose(r, model)
            for ((node, buffer) in scratch) {
                val expected = reference[node] ?: FloatArray(buffer.size) // compose omits undriven nodes
                assertContentEquals(expected, buffer, "node $node diverged for rig $r")
            }
        }
    }

    /** Buffers are zeroed each call, so a weight from a previous frame never leaks into the next. */
    @Test
    fun composeInto_zeroesStaleWeightsFromThePreviousFrame() {
        val scratch = freshScratch()
        MorphWeights.composeInto(rig(VrmExpression.AA to 1f), model, scratch) // sets node 2 index 3 = 1
        assertEquals(1f, scratch.getValue(2)[3])
        MorphWeights.composeInto(rig(VrmExpression.BLINK to 0.8f), model, scratch) // AA released
        // The released AA target must be back to 0 (the blink re-opens), only BLINK's indices set.
        assertContentEquals(floatArrayOf(0.8f, 0.8f, 0f, 0f), scratch.getValue(2))
        assertContentEquals(floatArrayOf(0f), scratch.getValue(7))
    }

    /** Bone-driven gaze skips look morphs in composeInto too (matches compose). */
    @Test
    fun composeInto_skipsLookMorphs_whenGazeIsBoneDriven() {
        val withLook = model.copy(
            expressions = model.expressions + (VrmExpression.LOOK_LEFT to listOf(MorphBind(2, 2, 1f))),
        )
        val bone = VrmLookAt.defaultRange(LookAtType.BONE)
        val boneModel = withLook.copy(lookAt = VrmLookAt(LookAtType.BONE, bone, bone, bone, bone))
        val gaze = rig(VrmExpression.LOOK_LEFT to 1f, VrmExpression.AA to 0.5f)
        val scratch = freshScratch()
        MorphWeights.composeInto(gaze, boneModel, scratch)
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0.5f), scratch.getValue(2)) // index 2 (look) not set
    }
}
