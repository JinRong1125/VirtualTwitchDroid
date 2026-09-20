package com.example.virtualtwitchdroid.feature.avatar.rig

import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTrackingFrame
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class FaceRigMapperTest {
    private val eps = 0.001f

    /** Un-mirrored so the table can be checked literally. */
    private val mapper = FaceRigMapper(mirror = false)

    private fun weights(vararg pairs: Pair<ArkitBlendshape, Float>): Map<String, Float> =
        pairs.associate { (shape, value) -> shape.key to value }

    @Test
    fun visemes_followTheMappingTable() {
        val out = mapper.mapExpressions(
            weights(
                ArkitBlendshape.JAW_OPEN to 0.8f,
                ArkitBlendshape.MOUTH_PUCKER to 0.6f,
                ArkitBlendshape.MOUTH_FUNNEL to 0.4f,
                ArkitBlendshape.MOUTH_STRETCH_LEFT to 0.2f,
                ArkitBlendshape.MOUTH_STRETCH_RIGHT to 0.6f,
                ArkitBlendshape.MOUTH_SMILE_LEFT to 1.0f,
                ArkitBlendshape.MOUTH_SMILE_RIGHT to 0.5f,
            ),
        )
        assertEquals(0.8f, out.getValue(VrmExpression.AA), eps)
        assertEquals(0.6f, out.getValue(VrmExpression.OU), eps)
        assertEquals(0.4f, out.getValue(VrmExpression.OH), eps)
        assertEquals(0.4f, out.getValue(VrmExpression.IH), eps) // avg(0.2, 0.6)
        assertEquals(0.75f, out.getValue(VrmExpression.EE), eps) // avg(1.0, 0.5)
    }

    @Test
    fun blink_isPerEye_plusTheSharedMinimum() {
        val out = mapper.mapExpressions(
            weights(ArkitBlendshape.EYE_BLINK_LEFT to 0.9f, ArkitBlendshape.EYE_BLINK_RIGHT to 0.3f),
        )
        assertEquals(0.9f, out.getValue(VrmExpression.BLINK_LEFT), eps)
        assertEquals(0.3f, out.getValue(VrmExpression.BLINK_RIGHT), eps)
        assertEquals(0.3f, out.getValue(VrmExpression.BLINK), eps) // a wink is not a blink
    }

    @Test
    fun gaze_lookingLeft_isLeftEyeOutPlusRightEyeIn() {
        val out = mapper.mapExpressions(
            weights(ArkitBlendshape.EYE_LOOK_OUT_LEFT to 0.8f, ArkitBlendshape.EYE_LOOK_IN_RIGHT to 0.6f),
        )
        assertEquals(0.7f, out.getValue(VrmExpression.LOOK_LEFT), eps)
        assertEquals(0f, out.getValue(VrmExpression.LOOK_RIGHT), eps)
    }

    @Test
    fun gaze_upAndDown_averageBothEyes() {
        val out = mapper.mapExpressions(
            weights(
                ArkitBlendshape.EYE_LOOK_UP_LEFT to 1.0f,
                ArkitBlendshape.EYE_LOOK_UP_RIGHT to 0.5f,
                ArkitBlendshape.EYE_LOOK_DOWN_LEFT to 0.2f,
                ArkitBlendshape.EYE_LOOK_DOWN_RIGHT to 0.2f,
            ),
        )
        assertEquals(0.75f, out.getValue(VrmExpression.LOOK_UP), eps)
        assertEquals(0.2f, out.getValue(VrmExpression.LOOK_DOWN), eps)
    }

    @Test
    fun emotions_followTheMappingTable() {
        val out = mapper.mapExpressions(
            weights(
                ArkitBlendshape.MOUTH_SMILE_LEFT to 1.0f,
                ArkitBlendshape.MOUTH_SMILE_RIGHT to 1.0f,
                ArkitBlendshape.CHEEK_SQUINT_LEFT to 0.5f,
                ArkitBlendshape.CHEEK_SQUINT_RIGHT to 0.5f,
                ArkitBlendshape.BROW_DOWN_LEFT to 0.4f,
                ArkitBlendshape.BROW_DOWN_RIGHT to 0.8f,
                ArkitBlendshape.BROW_INNER_UP to 0.9f,
                ArkitBlendshape.MOUTH_FROWN_LEFT to 0.3f,
                ArkitBlendshape.MOUTH_FROWN_RIGHT to 0.3f,
                ArkitBlendshape.EYE_WIDE_LEFT to 1.0f,
                ArkitBlendshape.EYE_WIDE_RIGHT to 1.0f,
                ArkitBlendshape.BROW_OUTER_UP_LEFT to 0.0f,
                ArkitBlendshape.BROW_OUTER_UP_RIGHT to 0.0f,
            ),
        )
        assertEquals(0.75f, out.getValue(VrmExpression.HAPPY), eps) // avg(1, 1, .5, .5)
        assertEquals(0.6f, out.getValue(VrmExpression.ANGRY), eps) // avg(.4, .8)
        assertEquals(0.5f, out.getValue(VrmExpression.SAD), eps) // avg(.9, .3, .3)
        assertEquals(0.5f, out.getValue(VrmExpression.SURPRISED), eps) // avg(1, 1, 0, 0)
    }

    @Test
    fun missingBlendshapes_readAsZero_andEveryDrivenPresetIsPresent() {
        val out = mapper.mapExpressions(emptyMap())
        val driven = VrmExpression.entries - VrmExpression.RELAXED - VrmExpression.NEUTRAL
        assertEquals(driven.toSet(), out.keys)
        assertTrue(out.values.all { it == 0f })
    }

    @Test
    fun outOfRangeCoefficients_areClampedToUnitInterval() {
        val out = mapper.mapExpressions(
            weights(ArkitBlendshape.JAW_OPEN to 1.7f, ArkitBlendshape.MOUTH_PUCKER to -0.4f),
        )
        assertEquals(1f, out.getValue(VrmExpression.AA), eps)
        assertEquals(0f, out.getValue(VrmExpression.OU), eps)
    }

    @Test
    fun mirror_swapsLeftAndRightExpressions() {
        val mirrored = FaceRigMapper(mirror = true).mapExpressions(
            weights(
                ArkitBlendshape.EYE_BLINK_LEFT to 1.0f, // the streamer winks their left eye…
                ArkitBlendshape.EYE_LOOK_OUT_LEFT to 1.0f, // …and glances to their left
                ArkitBlendshape.EYE_LOOK_IN_RIGHT to 1.0f,
            ),
        )
        // …which in the mirror shows on the right.
        assertEquals(1f, mirrored.getValue(VrmExpression.BLINK_RIGHT), eps)
        assertEquals(0f, mirrored.getValue(VrmExpression.BLINK_LEFT), eps)
        assertEquals(1f, mirrored.getValue(VrmExpression.LOOK_RIGHT), eps)
        assertEquals(0f, mirrored.getValue(VrmExpression.LOOK_LEFT), eps)
    }

    @Test
    fun noFaceFrame_mapsToTheNeutralRig() {
        assertEquals(FaceRig.NEUTRAL, mapper.map(FaceTrackingFrame.noFace(timestampMs = 42)))
    }

    @Test
    fun map_combinesExpressionsAndHeadPose_withMatchingMirrorConvention() {
        val frame = FaceTrackingFrame(
            blendshapes = weights(ArkitBlendshape.JAW_OPEN to 0.5f),
            headMatrixColumnMajor = RotationMath.rotationMatrix(HeadPose(yaw = 20f, pitch = 5f, roll = -8f)),
            timestampMs = 1,
            faceDetected = true,
        )
        val rig = FaceRigMapper(mirror = true).map(frame)
        assertTrue(rig.faceDetected)
        assertEquals(0.5f, rig[VrmExpression.AA], eps)
        // Mirrored: yaw and roll flip, pitch does not.
        assertEquals(-20f, rig.head.yaw, 0.01f)
        assertEquals(5f, rig.head.pitch, 0.01f)
        assertEquals(8f, rig.head.roll, 0.01f)
    }
}
