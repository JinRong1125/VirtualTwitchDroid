package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * Turns a tracker's facial transformation matrix into a [HeadPose] and a [HeadOffset], in the **face frame**
 * (+X = subject's left, +Y up, +Z toward the camera — how a VRM 1.0 model faces, so the angles apply to a 1.0
 * head bone as-is; the renderer converts for a 0.x model with [HeadPose.forModelFacingNegativeZ]).
 *
 * [mirror] — the "selfie" look: the streamed avatar should turn and move the way the streamer sees
 * themselves in a mirror, i.e. yaw, roll and the lateral offset are negated (pitch and depth are unaffected
 * by a horizontal mirror).
 */
class HeadPoseSolver(private val mirror: Boolean = true) {
    /** The head pose for a column-major 4×4 matrix, or [HeadPose.IDENTITY] when there is none. */
    fun solve(headMatrixColumnMajor: FloatArray?): HeadPose {
        if (headMatrixColumnMajor == null) return HeadPose.IDENTITY
        val raw = RotationMath.toHeadPose(headMatrixColumnMajor)
        return if (mirror) HeadPose(yaw = -raw.yaw, pitch = raw.pitch, roll = -raw.roll) else raw
    }

    /**
     * The head's position from the matrix's translation column (MediaPipe reports it in centimetres), or
     * null when there is no matrix (so nobody mistakes "unknown" for "at the origin").
     */
    fun solveOffset(headMatrixColumnMajor: FloatArray?): HeadOffset? {
        val m = headMatrixColumnMajor ?: return null
        return HeadOffset(x = if (mirror) -m[12] else m[12], y = m[13], z = m[14])
    }
}
