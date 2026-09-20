package com.example.virtualtwitchdroid.feature.avatar

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualtwitchdroid.core.common.media.MouthTrackSource
import com.example.virtualtwitchdroid.core.common.media.SpeechStreamSource
import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRigPipeline
import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTracker
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStats
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Owns the [FaceTracker] for the avatar screen and turns its raw frames into an [AvatarUiState]: the
 * smoothed rig comes from the shared [FaceRigPipeline] (staleness + lens-generation guards, selfie
 * mirror on the front lens); the stats decay to zero after [STALE_STATS_MS] without a new result. The
 * tracker is heavy (a loaded model), so it lives and dies with this ViewModel.
 */
@HiltViewModel
class AvatarViewModel internal constructor(
    val tracker: FaceTracker,
    /** The Zundamon voice (null in tests): the tab hosts its rehearsal controls. */
    val voice: SpeechStreamSource?,
    /** Its mouth track, handed to the renderer so rehearsed speech moves the avatar's lips. */
    val mouthTrack: MouthTrackSource?,
    clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(tracker: FaceTracker, voice: SpeechStreamSource, mouthTrack: MouthTrackSource) :
        this(tracker, voice, mouthTrack, SystemClock::uptimeMillis)

    internal constructor(tracker: FaceTracker, clock: () -> Long) : this(tracker, null, null, clock)

    private val _frontCamera = MutableStateFlow(true)

    /** Which lens feeds the tracker; the screen binds the camera accordingly. */
    val frontCamera: StateFlow<Boolean> = _frontCamera.asStateFlow()

    private val pipeline = FaceRigPipeline(clock = clock)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val freshStats = tracker.stats.transformLatest { stats ->
        emit(stats)
        delay(STALE_STATS_MS)
        emit(TrackerStats.EMPTY)
    }

    val uiState: StateFlow<AvatarUiState> = pipeline.rigs(tracker.frames)
        .combine(freshStats) { rig, stats -> rig to stats }
        .combine(tracker.status) { (rig, stats), status -> AvatarUiState(rig, stats, status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvatarUiState())

    fun toggleCamera() {
        val front = !_frontCamera.value
        _frontCamera.value = front
        pipeline.setLens(front)
    }

    override fun onCleared() {
        tracker.close()
    }

    internal companion object {
        const val STALE_FRAME_MS = FaceRigPipeline.STALE_FRAME_MS
        const val STALE_STATS_MS = 1_000L
    }
}
