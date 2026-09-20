package com.example.virtualtwitchdroid.core.common.media

import kotlinx.coroutines.flow.StateFlow

/** The five VRM mouth presets (`aa ih ou ee oh`), each `0..1`. */
data class VisemeFrame(val aa: Float, val ih: Float, val ou: Float, val ee: Float, val oh: Float) {
    companion object {
        val CLOSED = VisemeFrame(0f, 0f, 0f, 0f, 0f)
    }
}

/**
 * Timeline-exact lip-sync for the Zundamon voice: while [active], the avatar's mouth is driven by this
 * (sampled on the renderer's own frame clock) instead of the face tracker, because viewers hear Zundamon,
 * not the streamer. Implemented by `:feature:voice`, consumed by `:feature:avatar`.
 */
interface MouthTrackSource {
    /** True while a Zundamon-voice session is on (mouth owned by the voice, closed between utterances). */
    val active: StateFlow<Boolean>

    /** The mouth shape at [uptimeNanos] (the same monotonic clock as `Choreographer.frameTimeNanos`). */
    fun sample(uptimeNanos: Long): VisemeFrame
}
