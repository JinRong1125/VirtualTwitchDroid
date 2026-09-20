package com.example.virtualtwitchdroid.feature.avatar.tracking

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * A face-tracking backend: push camera frames in, observe [FaceTrackingFrame]s out. Results arrive
 * asynchronously (the backend runs inference on its own thread), so [frames] is a hot state flow
 * holding the latest result — consumers must treat a result that stops updating as stale (see
 * `AvatarViewModel`). [close] releases the model; the tracker is single-use after that.
 */
interface FaceTracker {
    /** The latest tracking result; starts as a "no face" frame. */
    val frames: StateFlow<FaceTrackingFrame>

    /** Live throughput/latency, for the HUD and the Phase-0 performance budget. */
    val stats: StateFlow<TrackerStats>

    /** Whether the model is starting, running (and on which delegate), or permanently failed. */
    val status: StateFlow<TrackerStatus>

    /**
     * Analyze one camera frame captured at [timestampMs] (a monotonic clock — timestamps must strictly
     * increase). [rotationDegrees] is the clockwise rotation that makes the frame upright; the backend
     * applies it, so callers hand over the sensor image as-is (no rotated copy). Called from the
     * camera's single analysis thread.
     */
    fun process(frame: Bitmap, rotationDegrees: Int, timestampMs: Long)

    fun close()
}

/** Tracking throughput: results per second over the last second and the latest inference latency. */
data class TrackerStats(val fps: Float = 0f, val inferenceMs: Long = 0L) {
    companion object {
        val EMPTY = TrackerStats()
    }
}

/** Lifecycle of the tracking model. [Failed] is terminal for this tracker instance. */
sealed interface TrackerStatus {
    data object Starting : TrackerStatus

    /** Inference is running on [delegate] (e.g. `GPU` / `CPU`). */
    data class Running(val delegate: String) : TrackerStatus

    data class Failed(val reason: FailureReason) : TrackerStatus

    enum class FailureReason {
        /** The model could not be initialized on any delegate (missing/corrupt asset, unsupported device). */
        INIT_FAILED,

        /** Inference started failing at runtime and no further fallback was available. */
        RUNTIME_ERROR,
    }
}
