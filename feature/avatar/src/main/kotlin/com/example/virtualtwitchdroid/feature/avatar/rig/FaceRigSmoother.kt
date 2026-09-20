package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * Exponential-moving-average smoothing of a [FaceRig] stream: tracker output is jittery frame to frame,
 * and an unsmoothed avatar shivers. Every channel (each expression weight and each head angle) is
 * filtered independently with `out = prev + alpha * (in − prev)`.
 *
 * @param alpha the blend toward the newest sample per frame, in `(0, 1]`; 1 = no smoothing. Around
 *   0.4–0.6 at 30 fps keeps fast mouth motion legible while killing jitter. Not time-compensated: pick
 *   it for the tracker's actual frame rate.
 */
class FaceRigSmoother(private val alpha: Float) {
    init {
        require(alpha > 0f && alpha <= 1f) { "alpha must be in (0, 1], got $alpha" }
    }

    private var previous: FaceRig? = null

    /** The smoothed rig for the next raw [rig]; the very first sample passes through unchanged. */
    fun next(rig: FaceRig): FaceRig {
        val prev = previous ?: return rig.also { previous = it }
        val keys = prev.expressions.keys + rig.expressions.keys
        val expressions = keys.associateWith { key -> lerp(prev[key], rig[key]) }
        val head = HeadPose(
            yaw = lerp(prev.head.yaw, rig.head.yaw),
            pitch = lerp(prev.head.pitch, rig.head.pitch),
            roll = lerp(prev.head.roll, rig.head.roll),
        )
        // An unknown position on either side is not smoothed toward: the newest known value stands.
        val offset = rig.headOffset?.let { new ->
            prev.headOffset?.let { old -> HeadOffset(lerp(old.x, new.x), lerp(old.y, new.y), lerp(old.z, new.z)) }
                ?: new
        }
        return FaceRig(expressions, head, rig.faceDetected, offset).also { previous = it }
    }

    /** Forget history — e.g. after tracking was lost for a while, to avoid a slow "swim" back. */
    fun reset() {
        previous = null
    }

    private fun lerp(from: Float, to: Float): Float = from + alpha * (to - from)
}
