package com.example.virtualtwitchdroid.feature.avatar.vrm

import com.example.virtualtwitchdroid.feature.avatar.render.BUNDLED_AVATAR_ASSET
import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtType
import com.example.virtualtwitchdroid.feature.avatar.rig.UpperBodyBone
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Parses the **real** avatar asset (the one [BUNDLED_AVATAR_ASSET], shipped in `src/main/assets`) with the
 * production parser and checks everything the renderer drives resolves — so swapping the asset can't silently
 * ship a model whose bones, expressions or spring bones don't resolve, or one that gltfio aborts on (a skin
 * with more than 256 joints is a native `PreconditionPanic`, not an exception). A missing asset is a failure,
 * not a skip: there is no CI here to notice a silently skipped gate.
 */
class BundledAvatarAssetTest {

    private fun repoRoot(): File {
        var dir: File = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile ?: break
        return dir
    }

    /** The asset as packaged in every build variant. */
    private fun asset(path: String): File {
        val file = File(repoRoot(), "feature/avatar/src/main/assets/$path")
        assertTrue(file.isFile, "avatar asset '$path' must be in src/main/assets (Git LFS not fetched?)")
        return file
    }

    @Test
    fun configuredAsset_isShippedInMain_andResolvesEverythingTheRendererDrives() = check(asset(BUNDLED_AVATAR_ASSET))

    private fun check(file: File) {
        val bytes = file.readBytes()
        val gltfJson = GlbReader.readJson(bytes)
        val model = VrmParser.parse(gltfJson)

        // The torso chain the face drives: head is mandatory, spine/chest/neck expected on any humanoid.
        for (bone in listOf(UpperBodyBone.HEAD, UpperBodyBone.NECK, UpperBodyBone.CHEST, UpperBodyBone.SPINE)) {
            val node = model.humanoidBones[bone.vrmName]
            assertNotNull(node, "${bone.vrmName} bone missing")
            val name = model.nodeNames.getOrNull(node)
            assertTrue(!name.isNullOrBlank(), "${bone.vrmName} node $node has no name (scene lookup is by name)")
            assertEquals(1, model.nodeNames.count { it == name }, "${bone.vrmName} node name '$name' must be unique")
        }
        // The mouth and the blink must be drivable, and every bound morph node must exist with enough targets.
        assertTrue(VrmExpression.AA in model.expressions, "no 'aa' expression")
        assertTrue(
            VrmExpression.BLINK in model.expressions || VrmExpression.BLINK_LEFT in model.expressions,
            "no blink",
        )
        model.expressions.values.flatten().forEach { bind ->
            val count = model.morphTargetCounts[bind.node]
            assertNotNull(count, "morph node ${bind.node} has no morph targets")
            assertTrue(bind.index < count, "morph index ${bind.index} out of range on node ${bind.node}")
        }
        // Secondary motion: at least one chain with a real segment, every referenced node named uniquely.
        val springs = assertNotNull(model.springBones, "no spring bones")
        assertTrue(springs.springs.any { it.joints.size >= 2 }, "no spring chain with a segment")
        springs.nodes.forEach { node ->
            val name = model.nodeNames.getOrNull(node)
            assertTrue(
                !name.isNullOrBlank() && model.nodeNames.count { it == name } == 1,
                "spring node $node needs a unique name",
            )
        }
        // Filament's skinning aborts the process on a skin with more than 256 joints (VRoid exports bind
        // every mesh to the whole skeleton — run scripts/vrm-prune-skins.py on such a model first).
        val skins = Json.parseToJsonElement(gltfJson).jsonObject["skins"]?.jsonArray.orEmpty()
        skins.forEachIndexed { i, skin ->
            val joints = skin.jsonObject.getValue("joints").jsonArray.size
            assertTrue(joints <= MAX_FILAMENT_BONES, "skin $i has $joints joints > $MAX_FILAMENT_BONES (gltfio aborts)")
            skin.jsonObject["inverseBindMatrices"]?.jsonPrimitive?.int?.let { ibm ->
                val count = Json.parseToJsonElement(gltfJson).jsonObject.getValue("accessors").jsonArray[ibm]
                    .jsonObject.getValue("count").jsonPrimitive.int
                assertEquals(joints, count, "skin $i inverse-bind matrix count must match its joints")
            }
        }
        // Gaze: a bone-driven model must expose both eye bones by unique name; every model has range maps.
        val lookAt = assertNotNull(model.lookAt, "no lookAt")
        if (lookAt.type == LookAtType.BONE) {
            for (node in listOf(model.leftEyeNode, model.rightEyeNode)) {
                assertNotNull(node, "bone look-at without an eye bone")
                val name = model.nodeNames.getOrNull(node)
                assertTrue(
                    !name.isNullOrBlank() &&
                        model.nodeNames.count {
                            it == name
                        } == 1,
                    "eye node $node needs a unique name",
                )
            }
        }
        assertTrue(
            lookAt.horizontalOuter.outputScale > 0f && lookAt.verticalDown.outputScale > 0f,
            "degenerate lookAt ranges",
        )
        // The container rewrite gltfio depends on must keep the file well-formed: header length matches,
        // the JSON chunk still parses, no escaped MIME slashes remain, and every MToon material is unlit.
        val (rewritten, prepared) = VrmContainer.prepareForGltfio(bytes)
        val declaredLength = java.nio.ByteBuffer.wrap(rewritten, 8, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        assertEquals(rewritten.size, declaredLength, "GLB header length must match the rewritten file")
        assertEquals(0, rewritten.size % 4, "GLB must stay 4-byte aligned")
        val rewrittenJson = GlbReader.readJson(rewritten)
        assertTrue(!rewrittenJson.contains("\\/"), "escaped slashes must be gone")
        assertEquals(model.title, VrmParser.parse(rewrittenJson).title)
        assertTrue(prepared.mtoon.mtoonMaterials > 0, "a VRM character is expected to use MToon")
        val (again, summary) = MToonMaterials.ensureUnlit(rewrittenJson)
        assertEquals(0, summary.unlitInjected, "every MToon material must be unlit after the rewrite")
        assertEquals(rewrittenJson, again)
    }

    private companion object {
        const val MAX_FILAMENT_BONES = 256
    }
}
