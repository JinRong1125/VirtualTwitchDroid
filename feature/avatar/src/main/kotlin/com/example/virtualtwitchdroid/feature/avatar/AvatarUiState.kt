package com.example.virtualtwitchdroid.feature.avatar

import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRig
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStats
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStatus

/** What the avatar screen shows: the smoothed rig driving the (future) avatar plus tracker health. */
data class AvatarUiState(
    val rig: FaceRig = FaceRig.NEUTRAL,
    val stats: TrackerStats = TrackerStats.EMPTY,
    val status: TrackerStatus = TrackerStatus.Starting,
)
