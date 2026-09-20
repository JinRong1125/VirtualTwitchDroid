package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * What the head's displacement does to the body: a torso lean ([torso], degrees on the [HeadPose] axes —
 * only pitch and roll are used) and a shift of the whole model ([shiftX]/[shiftY], metres, model axes).
 */
data class BodyLean(val torso: HeadPose, val shiftX: Float, val shiftY: Float) {
    companion object {
        val NONE = BodyLean(HeadPose.IDENTITY, 0f, 0f)
    }
}

/**
 * Maps a [HeadOffset] (cm from neutral) to a [BodyLean]. Face-only tracking has no torso signal, so this is
 * the standard VTuber approximation of "the body goes where the head goes": shifting sideways leans the
 * torso that way (roll — the crown moves toward the side the head moved to) and slides the model along; moving
 * toward the camera leans the torso forward (pitch); bobbing up/down nudges the model vertically. Gains are
 * per centimetre; every output is clamped so a tracker glitch can never fold the avatar over.
 */
object BodyLeanMapper {
    fun map(offset: HeadOffset): BodyLean = BodyLean(
        torso = HeadPose(
            yaw = 0f,
            pitch = (offset.z * PITCH_DEG_PER_CM).coerceIn(-MAX_PITCH_DEG, MAX_PITCH_DEG),
            // Positive roll tilts the crown toward the subject's RIGHT (−X); a move to +X leans the other way.
            roll = (-offset.x * ROLL_DEG_PER_CM).coerceIn(-MAX_ROLL_DEG, MAX_ROLL_DEG),
        ),
        shiftX = (offset.x * METRES_PER_CM).coerceIn(-MAX_SHIFT_M, MAX_SHIFT_M),
        shiftY = (offset.y * METRES_PER_CM * BOB_GAIN).coerceIn(-MAX_SHIFT_M, MAX_SHIFT_M),
    )

    /** A 10 cm slide in the chair reads as a clear 12° lean plus a 10 cm slide of the avatar. */
    const val ROLL_DEG_PER_CM = 1.2f
    const val PITCH_DEG_PER_CM = 0.6f
    const val MAX_ROLL_DEG = 15f
    const val MAX_PITCH_DEG = 12f
    const val METRES_PER_CM = 0.01f
    const val MAX_SHIFT_M = 0.15f

    /** Vertical bob is damped: a real torso barely moves when the head bobs. */
    const val BOB_GAIN = 0.5f
}
