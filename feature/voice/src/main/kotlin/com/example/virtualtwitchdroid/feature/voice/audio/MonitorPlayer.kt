package com.example.virtualtwitchdroid.feature.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock

/** The local speaker path behind an interface so the controller can be tested with a fake. */
interface Monitor {
    /** The uptime (ns) at which the *next* utterance's first sample will be heard if [play] is called now. */
    fun nextAnchorNanos(): Long

    /** Queues [pcm] for playback (blocking until it is queued). */
    fun play(pcm: Pcm16)

    fun stop()
}

/**
 * Plays Zundamon through the phone's speaker/headphones when no broadcast sink is consuming (the streamer
 * rehearsing). The anchor accounts for what is still queued in the track plus the output path's latency;
 * callers schedule the lips *before* the blocking [play] so a long utterance's mouth is not late.
 */
class MonitorPlayer(private val sampleRate: Int) : Monitor {
    private var track: AudioTrack? = null
    private var written = 0L

    init {
        require(sampleRate > 0)
    }

    private fun track(): AudioTrack = track ?: AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        .setAudioFormat(
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
        )
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                sampleRate,
            ),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
        .also {
            it.play()
            track = it
        }

    override fun nextAnchorNanos(): Long {
        val t = track()
        val queuedFrames = (written - t.playbackHeadPosition.toLong()).coerceAtLeast(0L)
        return SystemClock.uptimeMillis() * NANOS_PER_MILLI + queuedFrames * NANOS_PER_SECOND / sampleRate +
            OUTPUT_LATENCY_NANOS
    }

    override fun play(pcm: Pcm16) {
        val t = track()
        var offset = 0
        while (offset < pcm.samples.size) {
            val n = t.write(pcm.samples, offset, pcm.samples.size - offset, AudioTrack.WRITE_BLOCKING)
            if (n <= 0) break
            offset += n
        }
        written += pcm.frames
    }

    override fun stop() {
        track?.let {
            runCatching {
                it.pause()
                it.flush()
            }
            it.release()
        }
        track = null
        written = 0L
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L

        /** Typical Android output path latency; the monitor is not the broadcast clock, so this is enough. */
        const val OUTPUT_LATENCY_NANOS = 80_000_000L
    }
}
