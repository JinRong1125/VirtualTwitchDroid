package com.example.virtualtwitchdroid.feature.avatar.rig

/** The two eye bones a `lookAt.type = bone` VRM rotates (optional humanoid bones). */
enum class Eye(val vrmName: String) {
    LEFT("leftEye"),
    RIGHT("rightEye"),
}

/**
 * Turns the tracked gaze — the rig's `lookLeft/Right/Up/Down` weights (subject's own left/right, already
 * mirrored for the selfie view) — into **eye-bone rotations** through the model's `lookAt` range maps, the
 * way UniVRM's `VRMLookAtBoneApplyer` does: a gaze angle is fed to the *inner* map for the eye turning
 * toward the nose and the *outer* map for the eye turning away, and to the *down* / *up* map vertically.
 *
 * MediaPipe's `eyeLook*` coefficients saturate at a full glance, so a weight of 1 is treated as a gaze of
 * [FULL_GAZE_DEG] (= the spec's default `inputMaxValue`): with a default range map the eye then rotates by
 * the author's full `outputScale`, and never past it. Angles come back as [HeadPose]s in the face frame
 * (yaw +: subject's left; pitch +: down) so the renderer applies them exactly like the head bone,
 * including the VRM 0.x conversion.
 */
object EyeLookAt {
    data class Gaze(val left: HeadPose, val right: HeadPose) {
        companion object {
            val STRAIGHT = Gaze(HeadPose.IDENTITY, HeadPose.IDENTITY)
        }
    }

    fun solve(rig: FaceRig, lookAt: VrmLookAt): Gaze {
        // Face-frame gaze: + = the subject's left / down (HeadPose conventions).
        val yawDeg = (rig[VrmExpression.LOOK_LEFT] - rig[VrmExpression.LOOK_RIGHT]).coerceIn(-1f, 1f) * FULL_GAZE_DEG
        val pitchDeg = (rig[VrmExpression.LOOK_DOWN] - rig[VrmExpression.LOOK_UP]).coerceIn(-1f, 1f) * FULL_GAZE_DEG
        val pitch = signed(pitchDeg, if (pitchDeg >= 0f) lookAt.verticalDown else lookAt.verticalUp)
        // Looking left: the LEFT eye turns outward (away from the nose), the RIGHT eye inward — and vice versa.
        val leftYaw = signed(yawDeg, if (yawDeg >= 0f) lookAt.horizontalOuter else lookAt.horizontalInner)
        val rightYaw = signed(yawDeg, if (yawDeg >= 0f) lookAt.horizontalInner else lookAt.horizontalOuter)
        return Gaze(left = HeadPose(leftYaw, pitch, 0f), right = HeadPose(rightYaw, pitch, 0f))
    }

    private fun signed(inputDeg: Float, range: LookAtRangeMap): Float {
        val magnitude = range.map(inputDeg)
        return if (inputDeg < 0f) -magnitude else magnitude
    }

    /** A saturated `eyeLook*` coefficient is read as this gaze angle (degrees). */
    const val FULL_GAZE_DEG = VrmLookAt.DEFAULT_INPUT_MAX_DEG
}
