package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.math.abs
import kotlin.math.min

/** How a VRM wants its gaze applied: by rotating the eye bones, or by blending `look*` expressions. */
enum class LookAtType { BONE, EXPRESSION }

/**
 * One `VRMC_vrm.lookAt.rangeMap*` curve (VRM 0.x `firstPerson.lookAt*` `{xRange, yRange}` maps onto it,
 * as UniVRM's migration does — the 0.x `curve` is always the identity ramp and is ignored): an input gaze
 * angle up to [inputMaxValue] degrees maps linearly onto `0..outputScale` — degrees of eye-bone rotation
 * for [LookAtType.BONE], an expression weight for [LookAtType.EXPRESSION].
 */
data class LookAtRangeMap(val inputMaxValue: Float, val outputScale: Float) {
    /** The mapped magnitude for a gaze angle of |[inputDeg]|, clamped at [inputMaxValue]. */
    fun map(inputDeg: Float): Float {
        if (inputMaxValue <= 0f) return 0f
        return min(abs(inputDeg), inputMaxValue) / inputMaxValue * outputScale
    }
}

/**
 * The model's gaze setup. `horizontalInner` is used when an eye turns toward the nose, `horizontalOuter`
 * when it turns away from it, so the two eyes read different curves for the same gaze.
 */
data class VrmLookAt(
    val type: LookAtType,
    val horizontalInner: LookAtRangeMap,
    val horizontalOuter: LookAtRangeMap,
    val verticalDown: LookAtRangeMap,
    val verticalUp: LookAtRangeMap,
) {
    companion object {
        /** UniVRM's defaults when a file omits a range map. */
        fun defaultRange(type: LookAtType): LookAtRangeMap {
            val outputScale = if (type == LookAtType.BONE) DEFAULT_BONE_OUTPUT_DEG else 1f
            return LookAtRangeMap(inputMaxValue = DEFAULT_INPUT_MAX_DEG, outputScale = outputScale)
        }

        const val DEFAULT_INPUT_MAX_DEG = 90f
        const val DEFAULT_BONE_OUTPUT_DEG = 10f
    }
}
