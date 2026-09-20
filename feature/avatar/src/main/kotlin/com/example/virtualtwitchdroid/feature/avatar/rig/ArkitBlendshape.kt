package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * The 52 ARKit-compatible blendshape categories emitted by MediaPipe Face Landmarker
 * (`outputFaceBlendshapes = true`). [key] is the exact category name MediaPipe reports, which is what
 * a [com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTrackingFrame] is keyed by.
 *
 * "Left"/"Right" are the **subject's** left/right (ARKit convention), not the viewer's. Note MediaPipe's
 * set has `_neutral` and no `tongueOut` (it is not ARKit's list verbatim).
 */
enum class ArkitBlendshape(val key: String) {
    NEUTRAL("_neutral"),
    BROW_DOWN_LEFT("browDownLeft"),
    BROW_DOWN_RIGHT("browDownRight"),
    BROW_INNER_UP("browInnerUp"),
    BROW_OUTER_UP_LEFT("browOuterUpLeft"),
    BROW_OUTER_UP_RIGHT("browOuterUpRight"),
    CHEEK_PUFF("cheekPuff"),
    CHEEK_SQUINT_LEFT("cheekSquintLeft"),
    CHEEK_SQUINT_RIGHT("cheekSquintRight"),
    EYE_BLINK_LEFT("eyeBlinkLeft"),
    EYE_BLINK_RIGHT("eyeBlinkRight"),
    EYE_LOOK_DOWN_LEFT("eyeLookDownLeft"),
    EYE_LOOK_DOWN_RIGHT("eyeLookDownRight"),
    EYE_LOOK_IN_LEFT("eyeLookInLeft"),
    EYE_LOOK_IN_RIGHT("eyeLookInRight"),
    EYE_LOOK_OUT_LEFT("eyeLookOutLeft"),
    EYE_LOOK_OUT_RIGHT("eyeLookOutRight"),
    EYE_LOOK_UP_LEFT("eyeLookUpLeft"),
    EYE_LOOK_UP_RIGHT("eyeLookUpRight"),
    EYE_SQUINT_LEFT("eyeSquintLeft"),
    EYE_SQUINT_RIGHT("eyeSquintRight"),
    EYE_WIDE_LEFT("eyeWideLeft"),
    EYE_WIDE_RIGHT("eyeWideRight"),
    JAW_FORWARD("jawForward"),
    JAW_LEFT("jawLeft"),
    JAW_OPEN("jawOpen"),
    JAW_RIGHT("jawRight"),
    MOUTH_CLOSE("mouthClose"),
    MOUTH_DIMPLE_LEFT("mouthDimpleLeft"),
    MOUTH_DIMPLE_RIGHT("mouthDimpleRight"),
    MOUTH_FROWN_LEFT("mouthFrownLeft"),
    MOUTH_FROWN_RIGHT("mouthFrownRight"),
    MOUTH_FUNNEL("mouthFunnel"),
    MOUTH_LEFT("mouthLeft"),
    MOUTH_LOWER_DOWN_LEFT("mouthLowerDownLeft"),
    MOUTH_LOWER_DOWN_RIGHT("mouthLowerDownRight"),
    MOUTH_PRESS_LEFT("mouthPressLeft"),
    MOUTH_PRESS_RIGHT("mouthPressRight"),
    MOUTH_PUCKER("mouthPucker"),
    MOUTH_RIGHT("mouthRight"),
    MOUTH_ROLL_LOWER("mouthRollLower"),
    MOUTH_ROLL_UPPER("mouthRollUpper"),
    MOUTH_SHRUG_LOWER("mouthShrugLower"),
    MOUTH_SHRUG_UPPER("mouthShrugUpper"),
    MOUTH_SMILE_LEFT("mouthSmileLeft"),
    MOUTH_SMILE_RIGHT("mouthSmileRight"),
    MOUTH_STRETCH_LEFT("mouthStretchLeft"),
    MOUTH_STRETCH_RIGHT("mouthStretchRight"),
    MOUTH_UPPER_UP_LEFT("mouthUpperUpLeft"),
    MOUTH_UPPER_UP_RIGHT("mouthUpperUpRight"),
    NOSE_SNEER_LEFT("noseSneerLeft"),
    NOSE_SNEER_RIGHT("noseSneerRight"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The blendshape for a MediaPipe category name, or null for an unknown name. */
        fun fromKey(key: String): ArkitBlendshape? = byKey[key]

        /**
         * The same blendshape map with every subject-Left/Right pair swapped — how a front ("selfie")
         * camera image reads once mirrored. Keys without a side (e.g. `jawOpen`) pass through.
         */
        fun mirrored(blendshapes: Map<String, Float>): Map<String, Float> =
            blendshapes.mapKeys { (key, _) -> mirroredKeys[key] ?: mirrorKey(key) }

        /** Built once: 52 string concatenations per frame were the mapper's main allocation. */
        private val mirroredKeys: Map<String, String> = entries.associate { it.key to mirrorKey(it.key) }

        private fun mirrorKey(key: String): String = when {
            key.endsWith("Left") -> key.removeSuffix("Left") + "Right"
            key.endsWith("Right") -> key.removeSuffix("Right") + "Left"
            else -> key
        }
    }
}
