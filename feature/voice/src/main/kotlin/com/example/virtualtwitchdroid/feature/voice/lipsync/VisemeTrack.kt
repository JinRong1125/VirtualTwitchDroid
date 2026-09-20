package com.example.virtualtwitchdroid.feature.voice.lipsync

import com.example.virtualtwitchdroid.core.common.media.VisemeFrame
import kotlin.math.max
import kotlin.math.min

/** One VOICEVOX mora: an optional consonant (with its length) and a vowel, in seconds before `speedScale`. */
data class MoraTiming(val consonantSeconds: Float?, val vowel: String, val vowelSeconds: Float)

/** One accent phrase: its moras and the optional pause after it. */
data class PhraseTiming(val moras: List<MoraTiming>, val pauseSeconds: Float?)

/** Everything an `AudioQuery` says about time, engine-agnostic so the track builder is pure JVM. */
data class UtteranceTiming(
    val phrases: List<PhraseTiming>,
    val prePhonemeSeconds: Float,
    val postPhonemeSeconds: Float,
    val speedScale: Float = 1f,
)

/**
 * A mouth-shape timeline for one utterance, built from VOICEVOX's mora timings — phoneme-exact lip-sync.
 * Vowels map to the VRM presets (`a→aa i→ih u→ou e→ee o→oh`); `N`, `cl` and pauses close the mouth;
 * devoiced vowels (`A I U E O`) shape the mouth at a reduced weight. Adjacent vowels **crossfade** (a
 * release + attack per mora would flap visibly at ~8 moras/s); the mouth closes only at `N`/`cl`/pauses
 * and at the end. All times are divided by `speedScale`, as VOICEVOX does.
 */
class VisemeTrack private constructor(private val keys: List<Key>, val durationSeconds: Float) {
    private class Key(val timeSeconds: Float, val frame: VisemeFrame)

    /** The mouth at [timeSeconds] from the utterance's first sample (closed outside the track). */
    fun sample(timeSeconds: Float): VisemeFrame {
        if (keys.isEmpty() || timeSeconds < 0f || timeSeconds > durationSeconds) return VisemeFrame.CLOSED
        var i = keys.indexOfLast { it.timeSeconds <= timeSeconds }
        if (i < 0) return VisemeFrame.CLOSED
        if (i == keys.lastIndex) return keys[i].frame
        val a = keys[i]
        val b = keys[i + 1]
        val span = b.timeSeconds - a.timeSeconds
        val t = if (span <= 0f) 1f else ((timeSeconds - a.timeSeconds) / span).coerceIn(0f, 1f)
        return lerp(a.frame, b.frame, t)
    }

    companion object {
        /** A consonant longer than this still only takes this long to open the mouth. */
        const val ATTACK_SECONDS = 0.04f

        /** Closing at the end of speech / before a pause. */
        const val RELEASE_SECONDS = 0.06f
        const val VOICED_WEIGHT = 1f
        const val NARROW_WEIGHT = 0.8f
        const val DEVOICED_WEIGHT = 0.4f

        fun build(timing: UtteranceTiming): VisemeTrack {
            val speed = if (timing.speedScale > 0f) timing.speedScale else 1f
            val keys = ArrayList<Key>()
            var lastTime = 0f
            fun key(time: Float, frame: VisemeFrame) {
                lastTime = max(lastTime, time) // the sampler needs non-decreasing times
                keys += Key(lastTime, frame)
            }
            key(0f, VisemeFrame.CLOSED)
            var t = timing.prePhonemeSeconds / speed // where the current mora starts
            var shape = VisemeFrame.CLOSED // the mouth as it is at `t`
            for (phrase in timing.phrases) {
                for (mora in phrase.moras) {
                    val consonant = (mora.consonantSeconds ?: 0f) / speed
                    val vowelLen = mora.vowelSeconds / speed
                    val next = shapeOf(mora.vowel)
                    val moraEnd = t + consonant + vowelLen
                    if (next == VisemeFrame.CLOSED) {
                        // N / cl: the mouth closes over the release, no faster than the mora itself.
                        key(t, shape)
                        key(min(t + RELEASE_SECONDS, moraEnd), VisemeFrame.CLOSED)
                    } else {
                        // The previous shape holds until the attack begins; the vowel shape is reached at the
                        // vowel's onset. A vowel-only mora has no consonant to ramp through, so its attack is
                        // centred on the onset (half before, half after) instead of landing 40 ms late.
                        val vowelStart = t + consonant
                        val rampEnd = if (consonant > 0f) vowelStart else min(vowelStart + ATTACK_SECONDS / 2, moraEnd)
                        val rampStart = max(if (consonant > 0f) t else t - ATTACK_SECONDS / 2, rampEnd - ATTACK_SECONDS)
                        key(rampStart, shape)
                        key(rampEnd, next)
                    }
                    shape = next
                    t = moraEnd
                }
                val pause = phrase.pauseSeconds?.div(speed) ?: 0f
                if (pause > 0f) {
                    key(t, shape)
                    key(t + min(RELEASE_SECONDS, pause), VisemeFrame.CLOSED)
                    shape = VisemeFrame.CLOSED
                    t += pause
                }
            }
            val end = t + timing.postPhonemeSeconds / speed
            if (shape != VisemeFrame.CLOSED) {
                key(t, shape)
                key(min(t + RELEASE_SECONDS, end), VisemeFrame.CLOSED)
            }
            key(end, VisemeFrame.CLOSED)
            return VisemeTrack(keys, end)
        }

        /** The preset weights a VOICEVOX vowel symbol shapes the mouth with. */
        fun shapeOf(vowel: String): VisemeFrame = when (vowel) {
            "a" -> VisemeFrame(VOICED_WEIGHT, 0f, 0f, 0f, 0f)
            "i" -> VisemeFrame(0f, NARROW_WEIGHT, 0f, 0f, 0f)
            "u" -> VisemeFrame(0f, 0f, NARROW_WEIGHT, 0f, 0f)
            "e" -> VisemeFrame(0f, 0f, 0f, NARROW_WEIGHT, 0f)
            "o" -> VisemeFrame(0f, 0f, 0f, 0f, VOICED_WEIGHT)
            "A" -> VisemeFrame(DEVOICED_WEIGHT, 0f, 0f, 0f, 0f)
            "I" -> VisemeFrame(0f, DEVOICED_WEIGHT, 0f, 0f, 0f)
            "U" -> VisemeFrame(0f, 0f, DEVOICED_WEIGHT, 0f, 0f)
            "E" -> VisemeFrame(0f, 0f, 0f, DEVOICED_WEIGHT, 0f)
            "O" -> VisemeFrame(0f, 0f, 0f, 0f, DEVOICED_WEIGHT)
            else -> VisemeFrame.CLOSED // N, cl, pau, sil
        }

        private fun lerp(a: VisemeFrame, b: VisemeFrame, t: Float) = VisemeFrame(
            aa = a.aa + (b.aa - a.aa) * t,
            ih = a.ih + (b.ih - a.ih) * t,
            ou = a.ou + (b.ou - a.ou) * t,
            ee = a.ee + (b.ee - a.ee) * t,
            oh = a.oh + (b.oh - a.oh) * t,
        )
    }
}
