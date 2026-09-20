package com.example.virtualtwitchdroid.feature.voice.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoded 16-bit PCM. */
class Pcm16(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
    val frames: Int get() = samples.size / channels
    val durationSeconds: Float get() = frames / sampleRate.toFloat()
}

/**
 * Reads a RIFF/WAVE container (what VOICEVOX's `synthesis` returns) by walking its chunks to `fmt ` and
 * `data` — never assuming a 44-byte header. Only PCM 16-bit is accepted.
 */
object WavPcm {
    fun decode(wav: ByteArray): Pcm16 {
        require(wav.size >= HEADER_BYTES) { "not a WAV: ${wav.size} bytes" }
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == RIFF && buf.getInt(8) == WAVE) { "not a RIFF/WAVE file" }
        var pos = HEADER_BYTES
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var data: Pair<Int, Int>? = null
        while (pos + 8 <= wav.size) {
            val id = buf.getInt(pos)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                FMT -> {
                    require(buf.getShort(body).toInt() == 1) { "only PCM WAV is supported" }
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                DATA -> data = body to minOf(size, wav.size - body)
            }
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        val (start, length) = data ?: throw IllegalArgumentException("WAV has no data chunk")
        require(bits == 16 && channels in 1..2 && sampleRate > 0) {
            "unsupported WAV: $bits bit, $channels ch, $sampleRate Hz"
        }
        val samples = ShortArray(length / 2)
        buf.position(start)
        buf.asShortBuffer().get(samples)
        return Pcm16(samples, sampleRate, channels)
    }

    private const val HEADER_BYTES = 12
    private const val RIFF = 0x46464952 // "RIFF"
    private const val WAVE = 0x45564157 // "WAVE"
    private const val FMT = 0x20746D66 // "fmt "
    private const val DATA = 0x61746164 // "data"
}
