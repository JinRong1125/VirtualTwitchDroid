package com.example.virtualtwitchdroid.feature.voice.asr

/**
 * Silence added around a VAD segment before it is decoded. The Silero VAD cuts a segment at the very onset of
 * speech, and the offline Zipformer then tends to lose the first word (bench on a Pixel 8a: 「今日はいい天気ですね」
 * → 「いい天気ですね」, 「機械学習の…」 → 「学習の…」 in 6 of 11 sentences); the model saw leading/trailing silence
 * in training, so give it some. Pure, unit-tested.
 */
object SegmentPadding {
    const val LEAD_SECONDS = 0.4f
    const val TAIL_SECONDS = 0.3f

    fun pad(samples: FloatArray, sampleRate: Int, lead: Float = LEAD_SECONDS, tail: Float = TAIL_SECONDS): FloatArray {
        val leadN = (lead * sampleRate).toInt()
        val tailN = (tail * sampleRate).toInt()
        if (leadN <= 0 && tailN <= 0) return samples
        val out = FloatArray(leadN + samples.size + tailN) // zero-filled = digital silence
        samples.copyInto(out, destinationOffset = leadN)
        return out
    }
}
