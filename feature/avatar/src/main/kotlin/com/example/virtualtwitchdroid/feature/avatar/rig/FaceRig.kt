package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * The per-frame drive signal for an avatar: VRM expression weights (each `0..1`), the head pose, and the
 * head's displacement from neutral ([headOffset], centimetres — what the body leans/shifts with; null when
 * the tracker gave no position for this frame). Produced by [FaceRigMapper] + [HeadPoseSolver], baselined
 * by [OffsetBaseline], optionally smoothed by [FaceRigSmoother], and consumed by the renderer. Expressions
 * absent from [expressions] are 0.
 */
data class FaceRig(
    val expressions: Map<VrmExpression, Float>,
    val head: HeadPose,
    val faceDetected: Boolean,
    val headOffset: HeadOffset? = null,
) {
    /** The weight for [expression], 0 when not present. */
    operator fun get(expression: VrmExpression): Float = expressions[expression] ?: 0f

    companion object {
        /** No face: every expression 0, head straight, no displacement. */
        val NEUTRAL = FaceRig(emptyMap(), HeadPose.IDENTITY, faceDetected = false)
    }
}
