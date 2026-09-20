package com.example.virtualtwitchdroid.feature.avatar.rig

import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTrackingFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Turns a tracker's raw frames into a smoothed [FaceRig] stream:
 * `frame → FaceRigMapper → OffsetBaseline → FaceRigSmoother`, with two guards that keep a stopped tracker
 * from masquerading as a frozen-but-detected face:
 * - **Staleness** — a frame older than [staleFrameMs] with no successor is replaced by "no face" (a
 *   tracker's flow only ever holds the *last* result).
 * - **Lens generation** — after [setLens], frames captured before the switch are ignored, so the previous
 *   lens's last pose is never re-mapped through the new lens's mirror setting.
 *
 * The rig is mirrored only for the front ("selfie") lens; the head-offset neutral is re-captured on a lens
 * switch. Shared by the avatar screen's ViewModel and the broadcast (VTuber) session so both drive the
 * avatar identically.
 *
 * @param clock the same monotonic clock the frames are stamped with (`SystemClock.uptimeMillis`).
 */
class FaceRigPipeline(
    alpha: Float = SMOOTHING_ALPHA,
    private val staleFrameMs: Long = STALE_FRAME_MS,
    private val clock: () -> Long,
) {
    private data class Lens(val front: Boolean, val sinceMs: Long)

    private val lens = MutableStateFlow(Lens(front = true, sinceMs = 0L))
    private val mirroredMapper = FaceRigMapper(mirror = true)
    private val plainMapper = FaceRigMapper(mirror = false)
    private val baseline = OffsetBaseline()
    private val smoother = FaceRigSmoother(alpha)

    /**
     * Switch lens: only frames captured from now on count. This ONLY publishes the new lens — the smoother
     * and baseline are reset inside [rigs] when the change is observed, so all pipeline state is mutated on
     * the single collector coroutine (this may be called from the main thread while [rigs] runs off it).
     */
    fun setLens(front: Boolean) {
        lens.value = Lens(front, sinceMs = clock())
    }

    /** The smoothed rig for every [frames] emission (plus a relax-to-neutral when frames stall). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun rigs(frames: Flow<FaceTrackingFrame>): Flow<FaceRig> {
        var seenLens: Lens? = null
        return frames
            .transformLatest { frame ->
                emit(frame)
                delay(staleFrameMs)
                emit(FaceTrackingFrame.noFace(frame.timestampMs)) // nothing newer arrived: tracking stalled
            }
            .combine(lens) { frame, lens ->
                if (seenLens != null && seenLens != lens) {
                    smoother.reset() // a new lens is a new subject framing — don't swim from the old pose
                    baseline.reset() // …and a new camera geometry: re-measure where "neutral" is
                }
                seenLens = lens
                val current =
                    if (frame.timestampMs >= lens.sinceMs) frame else FaceTrackingFrame.noFace(frame.timestampMs)
                val mapped = (if (lens.front) mirroredMapper else plainMapper).map(current)
                baseline.relative(mapped, current.timestampMs)
            }
            .map { rig -> smoother.next(rig) }
    }

    companion object {
        /** ~30 fps tracking: kills jitter while a mouth opening still reads within ~2 frames. */
        const val SMOOTHING_ALPHA = 0.5f

        /** ~9 frames at 30 fps; long enough to ride out a dropped frame, short enough to not freeze. */
        const val STALE_FRAME_MS = 300L
    }
}
