package com.example.virtualtwitchdroid.feature.avatar.vrm

import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtRangeMap
import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtType
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Fixtures mirror the real Seed-san (VRM 1.0) and VRoid AvatarSample (VRM 0.x) layouts, trimmed. */
class VrmParserTest {

    private val v1 = """
        {
          "nodes": [
            {"name": "Root", "children": [1, 2]},
            {"name": "head 1", "rotation": [-0.08, 0, 0, 0.99]},
            {"name": "head", "mesh": 0}
          ],
          "meshes": [{"name": "head", "primitives": [{"targets": [{}, {}, {}, {}, {}, {}]}]}],
          "extensions": {
            "VRMC_springBone": {
              "specVersion": "1.0-beta",
              "colliders": [
                {"node": 0, "shape": {"capsule": {"offset": [0.04, 0, 0.02], "radius": 0.088, "tail": [-0.04, 0, 0.02]}}},
                {"node": 1, "shape": {"sphere": {"offset": [0, 0.1, 0], "radius": 0.09}}}
              ],
              "colliderGroups": [{"colliders": [0, 1]}],
              "springs": [
                {"name": "TailHair", "center": 0, "colliderGroups": [0],
                 "joints": [
                   {"node": 1, "hitRadius": 0.02, "stiffness": 4, "gravityPower": 0.1, "gravityDir": [0, -1, 0], "dragForce": 1},
                   {"node": 2}
                 ]}
              ]
            },
            "VRMC_vrm": {
              "specVersion": "1.0-beta",
              "meta": {"name": "Seed-san", "authors": ["VirtualCast, Inc."]},
              "humanoid": {"humanBones": {"hips": {"node": 0}, "neck": {"node": 0}, "head": {"node": 1}}},
              "expressions": {"preset": {
                "aa": {"morphTargetBinds": [{"node": 2, "index": 5, "weight": 1.0}]},
                "blink": {"morphTargetBinds": [{"node": 2, "index": 1, "weight": 1.0}, {"node": 2, "index": 2, "weight": 1.0}]},
                "happy": {"morphTargetBinds": [{"node": 2, "index": 3}]},
                "neutral": {},
                "lookUp": {"materialColorBinds": []}
              }}
            }
          }
        }
    """.trimIndent()

    private val v0 = """
        {
          "nodes": [{"name": "J_Bip_C_Head"}, {"name": "Face", "mesh": 0}, {"name": "Body", "mesh": 1}],
          "meshes": [
            {"name": "Face", "primitives": [{"targets": [{}, {}, {}, {}]}]},
            {"name": "Body", "primitives": [{}]}
          ],
          "extensions": {
            "VRM": {
              "exporterVersion": "VRoidStudio-0.13.0",
              "meta": {"title": "AvatarSample_A"},
              "humanoid": {"humanBones": [{"bone": "head", "node": 0}, {"bone": "hips", "node": 2}]},
              "blendShapeMaster": {"blendShapeGroups": [
                {"name": "A", "presetName": "a", "binds": [{"mesh": 0, "index": 3, "weight": 100}]},
                {"name": "Joy", "presetName": "joy", "binds": [{"mesh": 0, "index": 0, "weight": 50}]},
                {"name": "Custom", "presetName": "unknown", "binds": [{"mesh": 0, "index": 1, "weight": 100}]}
              ]},
              "secondaryAnimation": {
                "boneGroups": [{"comment": "hair", "stiffiness": 2, "gravityPower": 0.5, "gravityDir": {"x": 0, "y": -1, "z": 0},
                                "dragForce": 0.4, "center": -1, "hitRadius": 0.03, "bones": [0], "colliderGroups": [0]}],
                "colliderGroups": [{"node": 2, "colliders": [{"offset": {"x": 0, "y": 0.05, "z": 0.02}, "radius": 0.1}]}]
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun vrm1_resolvesBonesExpressionsMorphCountsAndNames() {
        val model = VrmParser.parse(v1)
        assertEquals(VrmVersion.V1, model.version)
        assertFalse(model.facesNegativeZ)
        assertEquals("Seed-san", model.title)
        assertEquals(1, model.headNode)
        assertEquals(listOf(MorphBind(2, 5, 1f)), model.expressions[VrmExpression.AA])
        assertEquals(2, model.expressions.getValue(VrmExpression.BLINK).size)
        assertEquals(1f, model.expressions.getValue(VrmExpression.HAPPY).single().weight) // weight defaults to 1
        assertFalse(VrmExpression.NEUTRAL in model.expressions) // no morph binds → not driven
        assertFalse(VrmExpression.LOOK_UP in model.expressions)
        assertEquals(mapOf(2 to 6), model.morphTargetCounts)
        assertEquals(listOf("Root", "head 1", "head"), model.nodeNames)
    }

    @Test
    fun vrm1_parsesSpringBones_collidersGroupsAndDefaults() {
        val springs = VrmParser.parse(v1).springBones!!
        assertEquals(2, springs.colliders.size)
        assertEquals(0.088f, springs.colliders[0].radius)
        assertEquals(listOf(-0.04f, 0f, 0.02f), springs.colliders[0].tail!!.toList()) // capsule
        assertEquals(null, springs.colliders[1].tail) // sphere
        val chain = springs.springs.single()
        assertEquals("TailHair", chain.name)
        assertEquals(0, chain.center)
        assertEquals(listOf(0, 1), chain.colliders) // group 0 flattened
        assertEquals(listOf(1, 2), chain.joints.map { it.node })
        assertEquals(4f, chain.joints[0].stiffness)
        assertEquals(1f, chain.joints[0].dragForce)
        assertEquals(1f, chain.joints[1].stiffness) // spec defaults on the tip joint
        assertEquals(setOf(0, 1, 2), springs.nodes)
    }

    @Test
    fun vrm0_secondaryAnimation_becomesChainsFollowingFirstChildren() {
        // Node 0 has no children in this fixture: a single-bone chain that only its virtual tail can animate.
        val springs = VrmParser.parse(v0).springBones!!
        val chain = springs.springs.single()
        assertEquals("hair", chain.name)
        assertEquals(listOf(0), chain.joints.map { it.node })
        assertTrue(chain.leafTail)
        assertEquals(2f, chain.joints[0].stiffness) // "stiffiness" spelling
        assertEquals(0.4f, chain.joints[0].dragForce)
        assertEquals(null, chain.center) // -1 ⇒ none
        assertEquals(listOf(0), chain.colliders)
        assertEquals(2, springs.colliders.single().node)
        // 0.x vectors are Unity-handed: Z is negated into glTF space.
        assertEquals(listOf(0f, 0.05f, -0.02f), springs.colliders.single().offset.toList())
        assertEquals(listOf(0f, -1f, -0f), chain.joints[0].gravityDir.toList())
    }

    @Test
    fun vrm0_mapsLegacyPresets_meshBindsToNodes_andPercentWeights() {
        val model = VrmParser.parse(v0)
        assertEquals(VrmVersion.V0, model.version)
        assertTrue(model.facesNegativeZ)
        assertEquals("AvatarSample_A", model.title)
        assertEquals(0, model.headNode)
        assertEquals(listOf(MorphBind(node = 1, index = 3, weight = 1f)), model.expressions[VrmExpression.AA])
        assertEquals(listOf(MorphBind(node = 1, index = 0, weight = 0.5f)), model.expressions[VrmExpression.HAPPY])
        assertEquals(2, model.expressions.size) // the unknown preset is dropped
        assertEquals(mapOf(1 to 4), model.morphTargetCounts) // Body has no targets
    }

    @Test
    fun vrm0_branchingSubtree_becomesOneChainPerFirstChildPath_allWithLeafTails() {
        //      0
        //     / \
        //    1   2
        //    |   | \
        //    3   4  5
        val children = mapOf(0 to listOf(1, 2), 1 to listOf(3), 2 to listOf(4, 5))
        assertEquals(listOf(listOf(0, 1, 3), listOf(2, 4), listOf(5)), VrmParser.subtreeChains(0, children))
        // A cycle in a malformed file terminates.
        assertEquals(listOf(listOf(0, 1)), VrmParser.subtreeChains(0, mapOf(0 to listOf(1), 1 to listOf(0))))

        val branching = v0.replace(
            """"nodes": [{"name": "J_Bip_C_Head"}, {"name": "Face", "mesh": 0}, {"name": "Body", "mesh": 1}]""",
            """"nodes": [{"name": "J_Bip_C_Head", "children": [1, 2]}, {"name": "Face", "mesh": 0}, {"name": "Body", "mesh": 1}]""",
        )
        val springs = VrmParser.parse(branching).springBones!!
        assertEquals(listOf(listOf(0, 1), listOf(2)), springs.springs.map { c -> c.joints.map { it.node } })
        assertTrue(springs.springs.all { it.leafTail && it.name == "hair" && it.joints.all { j -> j.stiffness == 2f } })
        assertEquals(setOf(0, 1, 2), springs.nodes)
    }

    @Test
    fun vrm1_lookAt_type_andRangeMaps_withDefaultsForMissingOnes() {
        val withLookAt = v1.replace(
            """"expressions": {""",
            """"lookAt": {"type": "expression", "offsetFromHeadBone": [0, 0.07, 0.1],
                 "rangeMapHorizontalInner": {"inputMaxValue": 60, "outputScale": 1.5},
                 "rangeMapVerticalDown": {"outputScale": 0.5}}, "expressions": {""",
        )
        val lookAt = VrmParser.parse(withLookAt).lookAt!!
        assertEquals(LookAtType.EXPRESSION, lookAt.type)
        assertEquals(LookAtRangeMap(60f, 1.5f), lookAt.horizontalInner)
        assertEquals(LookAtRangeMap(90f, 0.5f), lookAt.verticalDown) // inputMaxValue defaulted
        assertEquals(LookAtRangeMap(90f, 1f), lookAt.horizontalOuter) // expression default scale 1
        assertEquals(null, VrmParser.parse(v1).lookAt) // no lookAt block at all
        assertEquals(null, VrmParser.parse(v1).leftEyeNode)
    }

    @Test
    fun vrm0_lookAt_fromFirstPerson_xRangeIsInputMax_yRangeIsOutputScale_eyeBonesResolved() {
        val withLookAt = v0.replace(
            """"humanoid": {"humanBones": [{"bone": "head", "node": 0}, {"bone": "hips", "node": 2}]},""",
            """"humanoid": {"humanBones": [{"bone": "head", "node": 0}, {"bone": "hips", "node": 2},
                 {"bone": "leftEye", "node": 1}, {"bone": "rightEye", "node": 2}]},
               "firstPerson": {"lookAtTypeName": "Bone",
                 "lookAtHorizontalInner": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90, "yRange": 8},
                 "lookAtHorizontalOuter": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90, "yRange": 12},
                 "lookAtVerticalDown": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90, "yRange": 10}},""",
        )
        val model = VrmParser.parse(withLookAt)
        val lookAt = model.lookAt!!
        assertEquals(LookAtType.BONE, lookAt.type)
        assertEquals(LookAtRangeMap(90f, 8f), lookAt.horizontalInner)
        assertEquals(LookAtRangeMap(90f, 12f), lookAt.horizontalOuter)
        assertEquals(LookAtRangeMap(90f, 10f), lookAt.verticalDown)
        assertEquals(LookAtRangeMap(90f, 10f), lookAt.verticalUp) // missing → bone default 90/10
        assertEquals(1, model.leftEyeNode)
        assertEquals(2, model.rightEyeNode)
        val blendShape = withLookAt.replace(""""lookAtTypeName": "Bone"""", """"lookAtTypeName": "BlendShape"""")
        assertEquals(LookAtType.EXPRESSION, VrmParser.parse(blendShape).lookAt!!.type)
    }

    @Test
    fun nonVrmGltf_isRejected() {
        assertFailsWith<IllegalArgumentException> { VrmParser.parse("""{"asset":{"version":"2.0"},"extensions":{}}""") }
        assertFailsWith<IllegalArgumentException> { VrmParser.parse("""{"asset":{"version":"2.0"}}""") }
    }
}
