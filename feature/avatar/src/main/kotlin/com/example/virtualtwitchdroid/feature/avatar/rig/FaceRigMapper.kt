package com.example.virtualtwitchdroid.feature.avatar.rig

import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.BROW_DOWN_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.BROW_DOWN_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.BROW_INNER_UP
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.BROW_OUTER_UP_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.BROW_OUTER_UP_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.CHEEK_SQUINT_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.CHEEK_SQUINT_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_BLINK_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_BLINK_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_DOWN_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_DOWN_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_IN_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_IN_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_OUT_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_OUT_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_UP_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_LOOK_UP_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_WIDE_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.EYE_WIDE_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.JAW_OPEN
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_FROWN_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_FROWN_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_FUNNEL
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_PUCKER
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_SMILE_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_SMILE_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_STRETCH_LEFT
import com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape.MOUTH_STRETCH_RIGHT
import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTrackingFrame
import kotlin.math.min

/**
 * Maps a tracker's ARKit-52 blendshapes onto VRM expression presets — the face-only "rig solver" that
 * replaces a Kalidokit port (see `scripts/vtuber-avatar-plan.md` §4 for the table this implements).
 *
 * Rules are deliberately simple averages of the relevant ARKit coefficients, clamped to `0..1`;
 * emotions and visemes may co-fire and are reconciled by VRM's override rules at bind time. Blink is
 * exposed both per-eye ([VrmExpression.BLINK_LEFT]/[VrmExpression.BLINK_RIGHT]) and combined
 * ([VrmExpression.BLINK] = the shared part, `min(L, R)`) so a model can be driven with whichever
 * presets it defines — never both at once.
 *
 * Gaze uses the subject's own left/right: looking left = the left eye turns **out** and the right eye
 * turns **in**. With [mirror] (front-camera "selfie" view) every Left/Right pair is swapped first, so a
 * wink or a glance shows on the side the streamer sees in a mirror.
 */
class FaceRigMapper(
    private val mirror: Boolean = true,
    private val headPoseSolver: HeadPoseSolver = HeadPoseSolver(mirror = mirror),
) {
    /** The full rig for one tracking [frame]. A no-face frame yields [FaceRig.NEUTRAL]. */
    fun map(frame: FaceTrackingFrame): FaceRig {
        if (!frame.faceDetected) return FaceRig.NEUTRAL
        return FaceRig(
            expressions = mapExpressions(frame.blendshapes),
            head = headPoseSolver.solve(frame.headMatrixColumnMajor),
            faceDetected = true,
            // Absolute position here; the pipeline's OffsetBaseline turns it into an offset from neutral.
            headOffset = headPoseSolver.solveOffset(frame.headMatrixColumnMajor),
        )
    }

    /** VRM expression weights for a set of ARKit [blendshapes] (keyed by MediaPipe category name). */
    fun mapExpressions(blendshapes: Map<String, Float>): Map<VrmExpression, Float> {
        val b = if (mirror) ArkitBlendshape.mirrored(blendshapes) else blendshapes
        fun w(shape: ArkitBlendshape): Float = (b[shape.key] ?: 0f).coerceIn(0f, 1f)
        fun avg(vararg shapes: ArkitBlendshape): Float = shapes.map(::w).average().toFloat()

        val blinkLeft = w(EYE_BLINK_LEFT)
        val blinkRight = w(EYE_BLINK_RIGHT)
        return mapOf(
            // Visemes
            VrmExpression.AA to w(JAW_OPEN),
            VrmExpression.IH to avg(MOUTH_STRETCH_LEFT, MOUTH_STRETCH_RIGHT),
            VrmExpression.OU to w(MOUTH_PUCKER),
            VrmExpression.EE to avg(MOUTH_SMILE_LEFT, MOUTH_SMILE_RIGHT),
            VrmExpression.OH to w(MOUTH_FUNNEL),
            // Blink
            VrmExpression.BLINK_LEFT to blinkLeft,
            VrmExpression.BLINK_RIGHT to blinkRight,
            VrmExpression.BLINK to min(blinkLeft, blinkRight),
            // Gaze (subject's left/right)
            VrmExpression.LOOK_UP to avg(EYE_LOOK_UP_LEFT, EYE_LOOK_UP_RIGHT),
            VrmExpression.LOOK_DOWN to avg(EYE_LOOK_DOWN_LEFT, EYE_LOOK_DOWN_RIGHT),
            VrmExpression.LOOK_LEFT to avg(EYE_LOOK_OUT_LEFT, EYE_LOOK_IN_RIGHT),
            VrmExpression.LOOK_RIGHT to avg(EYE_LOOK_IN_LEFT, EYE_LOOK_OUT_RIGHT),
            // Emotions
            VrmExpression.HAPPY to avg(MOUTH_SMILE_LEFT, MOUTH_SMILE_RIGHT, CHEEK_SQUINT_LEFT, CHEEK_SQUINT_RIGHT),
            VrmExpression.ANGRY to avg(BROW_DOWN_LEFT, BROW_DOWN_RIGHT),
            VrmExpression.SAD to avg(BROW_INNER_UP, MOUTH_FROWN_LEFT, MOUTH_FROWN_RIGHT),
            VrmExpression.SURPRISED to avg(EYE_WIDE_LEFT, EYE_WIDE_RIGHT, BROW_OUTER_UP_LEFT, BROW_OUTER_UP_RIGHT),
        )
    }
}
