package com.example.virtualtwitchdroid.feature.avatar.vrm

import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmLookAt

/** Which VRM specification a model follows — they differ in facing direction and extension layout. */
enum class VrmVersion {
    /** `VRM` extension (0.x). Models face **−Z**. */
    V0,

    /** `VRMC_vrm` extension (1.0). Models face **+Z** like plain glTF. */
    V1,
}

/** One morph-target contribution of an expression: `weights[index] += expressionWeight * weight` on [node]. */
data class MorphBind(val node: Int, val index: Int, val weight: Float)

/**
 * The parts of a VRM file the avatar needs, resolved to glTF **node indices**:
 * - [humanoidBones]: VRM humanoid bone name (`head`, `neck`, …) → node.
 * - [expressions]: VRM expression preset → the morph targets it drives.
 * - [morphTargetCounts]: node → number of morph targets on its mesh (to size the weight arrays).
 * - [nodeNames]: glTF node names by index (how nodes are looked up in the rendered scene).
 * - [springBones]: the model's secondary-motion chains (hair, clothes…), or null when it has none.
 * - [lookAt]: how the gaze is applied (eye bones vs. `look*` expressions) and its range maps.
 * - [mtoon]: what [MToonMaterials] found (and fixed) in the file's materials.
 */
data class VrmModel(
    val version: VrmVersion,
    val title: String?,
    val humanoidBones: Map<String, Int>,
    val expressions: Map<VrmExpression, List<MorphBind>>,
    val morphTargetCounts: Map<Int, Int>,
    val nodeNames: List<String?>,
    val springBones: SpringBoneSet? = null,
    val lookAt: VrmLookAt? = null,
    val mtoon: MToonSummary = MToonSummary.NONE,
) {
    /**
     * True for VRM 0.x models, which face −Z: the renderer turns the root 180° so they face the camera and
     * converts bone angles with `HeadPose.forModelFacingNegativeZ` (pitch and roll flip, yaw does not).
     */
    val facesNegativeZ: Boolean get() = version == VrmVersion.V0

    val headNode: Int? get() = humanoidBones["head"]

    /** The eye bones a [LookAtType.BONE] model rotates for its gaze (optional humanoid bones). */
    val leftEyeNode: Int? get() = humanoidBones["leftEye"]
    val rightEyeNode: Int? get() = humanoidBones["rightEye"]
}
