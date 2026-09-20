package com.example.virtualtwitchdroid.feature.voice.audio

/**
 * Converts VOICEVOX's 24 kHz mono PCM to the encoder's rate and channel layout (44.1 kHz stereo today):
 * linear interpolation between neighbouring samples, then the mono sample duplicated into both channels,
 * as little-endian 16-bit bytes. Good enough for speech; no filtering needed at an up-conversion.
 */
object Resampler {
    fun toPcm16Bytes(mono: ShortArray, fromRate: Int, toRate: Int, stereo: Boolean): ByteArray {
        require(fromRate > 0 && toRate > 0) { "invalid rates $fromRate → $toRate" }
        if (mono.isEmpty()) return ByteArray(0)
        val outFrames = ((mono.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val channels = if (stereo) 2 else 1
        val out = ByteArray(outFrames * channels * 2)
        val step = fromRate.toDouble() / toRate
        var o = 0
        for (i in 0 until outFrames) {
            val pos = i * step
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()
            val a = mono[idx.coerceAtMost(mono.lastIndex)].toInt()
            val b = mono[(idx + 1).coerceAtMost(mono.lastIndex)].toInt()
            val s = (a + (b - a) * frac).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            repeat(channels) {
                out[o++] = (s and 0xFF).toByte()
                out[o++] = (s shr 8).toByte()
            }
        }
        return out
    }

    /** How many bytes one second of the sink's format is. */
    fun bytesPerSecond(sampleRate: Int, stereo: Boolean): Int = sampleRate * (if (stereo) 2 else 1) * 2
}
