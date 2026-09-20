package com.example.virtualtwitchdroid.feature.avatar.tracking

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double: tests drive [frames] / [stats] / [status] directly. */
class FakeFaceTracker : FaceTracker {
    override val frames = MutableStateFlow(FaceTrackingFrame.noFace(timestampMs = 0L))
    override val stats = MutableStateFlow(TrackerStats.EMPTY)
    override val status = MutableStateFlow<TrackerStatus>(TrackerStatus.Starting)

    var processedFrames = 0
        private set
    var closed = false
        private set

    override fun process(frame: Bitmap, rotationDegrees: Int, timestampMs: Long) {
        processedFrames++
    }

    override fun close() {
        closed = true
    }
}
