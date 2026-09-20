package com.example.virtualtwitchdroid.feature.avatar.rig

import com.example.virtualtwitchdroid.core.common.media.VisemeFrame
import kotlin.test.assertEquals
import org.junit.Test

class MouthOverrideTest {
    private val tracked = FaceRig(
        mapOf(
            VrmExpression.AA to 0.9f, // the streamer's open mouth
            VrmExpression.EE to 0.4f,
            VrmExpression.BLINK to 1f,
            VrmExpression.HAPPY to 0.8f,
            VrmExpression.LOOK_LEFT to 0.5f,
        ),
        HeadPose(10f, 5f, 2f),
        faceDetected = true,
    )

    @Test
    fun visemesComeFromTheVoice_trackedMouthIsDropped_restPassesThrough() {
        val out = MouthOverride.apply(tracked, VisemeFrame(aa = 0f, ih = 0f, ou = 0.8f, ee = 0f, oh = 0f))
        assertEquals(0f, out[VrmExpression.AA]) // tracked jaw ignored
        assertEquals(0f, out[VrmExpression.EE])
        assertEquals(0.8f, out[VrmExpression.OU])
        assertEquals(1f, out[VrmExpression.BLINK]) // untouched
        assertEquals(0.5f, out[VrmExpression.LOOK_LEFT])
        assertEquals(tracked.head, out.head)
        assertEquals(0.8f * MouthOverride.EMOTION_ATTENUATION, out[VrmExpression.HAPPY], 1e-6f)
    }

    @Test
    fun closedFrame_closesTheMouthCompletely() {
        val out = MouthOverride.apply(tracked, VisemeFrame.CLOSED)
        for (e in listOf(VrmExpression.AA, VrmExpression.IH, VrmExpression.OU, VrmExpression.EE, VrmExpression.OH)) {
            assertEquals(0f, out[e], "preset $e")
        }
    }
}
