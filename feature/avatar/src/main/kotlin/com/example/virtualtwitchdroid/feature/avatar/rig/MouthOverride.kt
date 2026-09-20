package com.example.virtualtwitchdroid.feature.avatar.rig

import com.example.virtualtwitchdroid.core.common.media.VisemeFrame

/**
 * While the Zundamon voice speaks for the streamer, the avatar's mouth belongs to the voice: the five
 * viseme presets are replaced by the sampled [VisemeFrame] (closed between utterances — viewers never hear
 * the streamer, so the tracked mouth would flap silently), and the mouth-shaped emotions are attenuated so a
 * smiling streamer does not fight the visemes. Blink, brows, head and gaze pass through untouched.
 */
object MouthOverride {
    /** Mouth-deforming emotion presets are scaled by this while the voice owns the mouth. */
    const val EMOTION_ATTENUATION = 0.3f

    private val visemes =
        listOf(VrmExpression.AA, VrmExpression.IH, VrmExpression.OU, VrmExpression.EE, VrmExpression.OH)
    private val mouthEmotions =
        setOf(VrmExpression.HAPPY, VrmExpression.SAD, VrmExpression.ANGRY, VrmExpression.SURPRISED)

    fun apply(rig: FaceRig, frame: VisemeFrame): FaceRig {
        val expressions = HashMap(rig.expressions)
        visemes.forEach { expressions.remove(it) }
        if (frame.aa > 0f) expressions[VrmExpression.AA] = frame.aa
        if (frame.ih > 0f) expressions[VrmExpression.IH] = frame.ih
        if (frame.ou > 0f) expressions[VrmExpression.OU] = frame.ou
        if (frame.ee > 0f) expressions[VrmExpression.EE] = frame.ee
        if (frame.oh > 0f) expressions[VrmExpression.OH] = frame.oh
        for (e in mouthEmotions) expressions[e]?.let { expressions[e] = it * EMOTION_ATTENUATION }
        return rig.copy(expressions = expressions)
    }
}
