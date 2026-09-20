package com.example.virtualtwitchdroid.feature.avatar.tracking

/**
 * One frame of raw face-tracking output, as produced by a tracker such as MediaPipe Face Landmarker.
 * This is the boundary between the tracking backend and the pure rig math in `rig/`: everything
 * downstream consumes this shape and nothing downstream knows about MediaPipe.
 *
 * @property blendshapes ARKit-style blendshape coefficients keyed by name (see
 *   [com.example.virtualtwitchdroid.feature.avatar.rig.ArkitBlendshape]), each in `0..1`. Missing keys are
 *   treated as 0 by the mapper.
 * @property headMatrixColumnMajor the 4×4 facial transformation matrix as 16 floats in **column-major**
 *   order (`m[col * 4 + row]`, as MediaPipe emits it), or null when the tracker didn't produce one.
 * @property timestampMs the frame's capture timestamp, for smoothing / fps measurement.
 * @property faceDetected false when no face was found; the rig should relax toward neutral.
 */
@Suppress("ArrayInDataClass") // the matrix is an opaque per-frame payload; identity semantics are fine
data class FaceTrackingFrame(
    val blendshapes: Map<String, Float>,
    val headMatrixColumnMajor: FloatArray?,
    val timestampMs: Long,
    val faceDetected: Boolean,
) {
    companion object {
        /** A "no face" frame at [timestampMs]. */
        fun noFace(timestampMs: Long): FaceTrackingFrame =
            FaceTrackingFrame(emptyMap(), null, timestampMs, faceDetected = false)
    }
}
