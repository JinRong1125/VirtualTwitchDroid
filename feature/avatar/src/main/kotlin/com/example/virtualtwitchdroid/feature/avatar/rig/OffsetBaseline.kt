package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.math.exp

/**
 * Turns the tracker's **absolute** head position into an offset from the streamer's **neutral** spot, so
 * the body leans with how they *move*, not with where their camera happens to sit.
 *
 * The neutral spot is captured on the first detected frame, and again whenever the face comes back after
 * being lost for at least [recaptureAfterMs] (a short dropout keeps the old neutral). Between captures it
 * drifts toward the current position with time constant [driftTimeConstantMs], so a sustained shift in the
 * chair slowly becomes the new neutral instead of a permanent lean. No-face frames report a zero offset.
 * Stateful; timestamps are the frames' own monotonic clock (milliseconds).
 */
class OffsetBaseline(
    private val driftTimeConstantMs: Long = DRIFT_TIME_CONSTANT_MS,
    private val recaptureAfterMs: Long = RECAPTURE_AFTER_MS,
) {
    private var baseline: HeadOffset? = null
    private var lastSeenMs = 0L

    /**
     * [rig] with its absolute [FaceRig.headOffset] replaced by the offset from neutral. A no-face frame
     * reports zero; a detected frame **without** a position (no head matrix) is passed through untouched —
     * it never captures or drifts the neutral spot.
     */
    fun relative(rig: FaceRig, timestampMs: Long): FaceRig {
        if (!rig.faceDetected) return rig.copy(headOffset = HeadOffset.ZERO)
        val absolute = rig.headOffset ?: return rig
        val previous = baseline
        val sinceLastSeenMs = timestampMs - lastSeenMs
        lastSeenMs = timestampMs
        if (previous == null || sinceLastSeenMs >= recaptureAfterMs) {
            baseline = absolute // (re)captured: this is neutral
            return rig.copy(headOffset = HeadOffset.ZERO)
        }
        val alpha = (1.0 - exp(-sinceLastSeenMs.toDouble() / driftTimeConstantMs)).toFloat()
        val drifted = HeadOffset(
            x = previous.x + alpha * (absolute.x - previous.x),
            y = previous.y + alpha * (absolute.y - previous.y),
            z = previous.z + alpha * (absolute.z - previous.z),
        )
        baseline = drifted
        return rig.copy(headOffset = absolute - drifted)
    }

    /** Forget the neutral spot — e.g. the camera lens changed, so the geometry did too. */
    fun reset() {
        baseline = null
    }

    companion object {
        /** A sustained shift becomes the new neutral over ~20 s (slow enough that a lean reads as a lean). */
        const val DRIFT_TIME_CONSTANT_MS = 20_000L

        /** Face gone this long ⇒ the streamer probably moved; re-measure neutral when they return. */
        const val RECAPTURE_AFTER_MS = 2_000L
    }
}
