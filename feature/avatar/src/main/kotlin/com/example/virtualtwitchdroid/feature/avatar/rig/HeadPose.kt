package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * Head orientation as Tait-Bryan angles in **degrees**, in the face's own right-handed frame: Y = up,
 * +Z = out of the face toward the camera, so +X = Y × Z = the **subject's left** (the viewer's right —
 * the same as image-right in MediaPipe's camera frame and as glTF/VRM 1.0's +X).
 *
 * Angles are canonical right-handed rotations (counter-clockwise looking down each +axis):
 * - [yaw] about +Y: positive turns the face toward the **subject's left** (the normal swings to +X).
 * - [pitch] about +X: positive tilts the face **down** (the normal swings toward −Y).
 * - [roll] about +Z: positive tilts the crown of the head toward the **subject's right** shoulder (−X).
 *
 * The composition order is `R = Rz(roll) · Ry(yaw) · Rx(pitch)` (see [RotationMath]). VRM 1.0 models
 * face +Z with Y up — the same frame — so these apply to the head bone directly. A mirrored "selfie" view
 * negates yaw and roll (see [HeadPoseSolver]); a VRM 0.x model needs [forModelFacingNegativeZ].
 */
data class HeadPose(val yaw: Float, val pitch: Float, val roll: Float) {
    /**
     * The same rotation expressed in the bone frame of a model that faces **−Z** (VRM 0.x: forward = −Z,
     * left = −X, up = +Y). That frame is ours rotated 180° about Y, so conjugating by `Ry(180°)` flips the X
     * and Z axes: **pitch and roll change sign, yaw does not** (a turn to the subject's left is still a
     * positive rotation about the shared +Y). Applying the face-frame angles unchanged would make a 0.x
     * model nod *up* when the streamer nods down.
     */
    fun forModelFacingNegativeZ(): HeadPose = HeadPose(yaw = yaw, pitch = -pitch, roll = -roll)

    companion object {
        val IDENTITY = HeadPose(0f, 0f, 0f)
    }
}
