package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * The head's displacement from its **neutral** position, in **centimetres**, in the face frame of
 * [HeadPose]: +X = the subject's left, +Y = up, +Z = toward the camera (closer). MediaPipe's facial
 * transformation matrix carries the absolute position in this unit; [OffsetBaseline] turns it into an
 * offset from where the streamer settled. Zero when unknown.
 */
data class HeadOffset(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: HeadOffset): HeadOffset = HeadOffset(x - o.x, y - o.y, z - o.z)

    companion object {
        val ZERO = HeadOffset(0f, 0f, 0f)
    }
}
