package com.example.virtualtwitchdroid.feature.voice.lipsync

import com.example.virtualtwitchdroid.core.common.media.VisemeFrame

/**
 * The tracks currently scheduled for playback, each **anchored** to the monotonic uptime (nanoseconds) at
 * which its first PCM sample is heard — the moment its bytes were handed to the audio sink plus that
 * sink's latency. The renderer samples it with `Choreographer.frameTimeNanos`; finished tracks are
 * dropped. Thread-safe: audio threads add, the render thread samples.
 */
class VisemeTimeline {
    private class Scheduled(val track: VisemeTrack, val anchorNanos: Long)

    private val scheduled = ArrayList<Scheduled>()
    private val lock = Any()

    fun add(track: VisemeTrack, anchorNanos: Long) = synchronized(lock) { scheduled += Scheduled(track, anchorNanos) }

    fun clear() = synchronized(lock) { scheduled.clear() }

    /** True while any track is still playing at [nowNanos] (or scheduled in the future). */
    fun isSpeaking(nowNanos: Long): Boolean = synchronized(lock) {
        scheduled.any { nowNanos <= it.anchorNanos + (it.track.durationSeconds * NANOS_PER_SECOND).toLong() }
    }

    /** Seconds of scheduled audio still to be heard after [nowNanos]. */
    fun remainingSeconds(nowNanos: Long): Float = synchronized(lock) {
        scheduled.maxOfOrNull {
            (it.anchorNanos + (it.track.durationSeconds * NANOS_PER_SECOND).toLong() - nowNanos) /
                NANOS_PER_SECOND
        }
            ?.coerceAtLeast(0f) ?: 0f
    }

    fun sample(nowNanos: Long): VisemeFrame = synchronized(lock) {
        scheduled.removeAll { nowNanos > it.anchorNanos + (it.track.durationSeconds * NANOS_PER_SECOND).toLong() }
        val current = scheduled.firstOrNull { nowNanos >= it.anchorNanos } ?: return VisemeFrame.CLOSED
        current.track.sample((nowNanos - current.anchorNanos) / NANOS_PER_SECOND)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
