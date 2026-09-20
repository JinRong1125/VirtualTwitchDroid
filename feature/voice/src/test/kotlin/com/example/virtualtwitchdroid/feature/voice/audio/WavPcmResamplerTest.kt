package com.example.virtualtwitchdroid.feature.voice.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class WavPcmResamplerTest {

    /** A WAV with an extra `LIST` chunk before `data` (so a 44-byte-header assumption would fail). */
    private fun wav(samples: ShortArray, rate: Int = 24_000, channels: Int = 1, extraChunk: Boolean = true): ByteArray {
        val list = if (extraChunk) ByteArray(10) { 'x'.code.toByte() } else ByteArray(0)
        val dataBytes = samples.size * 2
        val total = 12 + 24 + (if (extraChunk) 8 + list.size + (list.size % 2) else 0) + 8 + dataBytes
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x46464952).putInt(total - 8).putInt(0x45564157)
        buf.putInt(0x20746D66).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        if (extraChunk) {
            buf.putInt(0x5453494C).putInt(list.size).put(list)
            if (list.size % 2 == 1) buf.put(0)
        }
        buf.putInt(0x61746164).putInt(dataBytes)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }

    @Test
    fun decode_walksChunks_andReadsFormatAndSamples() {
        val samples = shortArrayOf(0, 1000, -1000, 32767, -32768)
        val pcm = WavPcm.decode(wav(samples))
        assertEquals(24_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertContentEquals(samples, pcm.samples)
        assertEquals(5f / 24_000f, pcm.durationSeconds, 1e-7f)
        assertContentEquals(samples, WavPcm.decode(wav(samples, extraChunk = false)).samples)
    }

    @Test
    fun decode_rejectsNonWav() {
        assertFailsWith<IllegalArgumentException> { WavPcm.decode(ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { WavPcm.decode("RIFF....WAVX".toByteArray() + ByteArray(40)) }
    }

    @Test
    fun resampler_upsamplesByLinearInterpolation_andDuplicatesToStereo() {
        val mono = shortArrayOf(0, 1000, 2000, 3000)
        val out = Resampler.toPcm16Bytes(mono, fromRate = 2, toRate = 4, stereo = true)
        // 4 frames in → 8 frames out, 2 channels, 2 bytes each.
        assertEquals(8 * 2 * 2, out.size)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        val frames = (0 until 8).map { buf.getShort(it * 4).toInt() to buf.getShort(it * 4 + 2).toInt() }
        // Every frame's L == R; values ramp 0, 500, 1000, 1500, 2000, 2500, 3000, 3000 (last clamps).
        assertEquals(listOf(0, 500, 1000, 1500, 2000, 2500, 3000, 3000), frames.map { it.first })
        assertEquals(frames.map { it.first }, frames.map { it.second })
    }

    @Test
    fun resampler_monoPassThrough_andBytesPerSecond() {
        val mono = shortArrayOf(5, -5, 7)
        val out = Resampler.toPcm16Bytes(mono, 24_000, 24_000, stereo = false)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(listOf(5, -5, 7), (0 until 3).map { buf.getShort(it * 2).toInt() })
        assertEquals(44_100 * 2 * 2, Resampler.bytesPerSecond(44_100, stereo = true))
        assertEquals(0, Resampler.toPcm16Bytes(ShortArray(0), 24_000, 44_100, true).size)
    }
}
