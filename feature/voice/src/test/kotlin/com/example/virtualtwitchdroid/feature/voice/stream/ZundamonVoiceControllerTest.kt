package com.example.virtualtwitchdroid.feature.voice.stream

import com.example.virtualtwitchdroid.core.common.media.PcmSink
import com.example.virtualtwitchdroid.core.common.media.VoiceState
import com.example.virtualtwitchdroid.feature.voice.VoiceSupport
import com.example.virtualtwitchdroid.feature.voice.asr.MicSource
import com.example.virtualtwitchdroid.feature.voice.asr.Recognised
import com.example.virtualtwitchdroid.feature.voice.asr.Recogniser
import com.example.virtualtwitchdroid.feature.voice.assets.VoicePaths
import com.example.virtualtwitchdroid.feature.voice.audio.Monitor
import com.example.virtualtwitchdroid.feature.voice.audio.Pcm16
import com.example.virtualtwitchdroid.feature.voice.lipsync.MoraTiming
import com.example.virtualtwitchdroid.feature.voice.lipsync.PhraseTiming
import com.example.virtualtwitchdroid.feature.voice.lipsync.UtteranceTiming
import com.example.virtualtwitchdroid.feature.voice.tts.Speaker
import com.example.virtualtwitchdroid.feature.voice.tts.Synthesized
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The voice controller's state machine on fakes: model preparation and its failure/recovery, the say() path
 * through the monitor (lips scheduled before the blocking write), delivery to a consuming broadcast sink
 * (anchor = write time + the sink's queued audio, no output latency added), the half-duplex gate, and
 * `active` releasing the avatar's mouth when nothing is left to speak. All dispatchers share the test
 * scheduler, so "threads" are deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZundamonVoiceControllerTest {

    /** Emits [result] once per requested chunk (default 1), like VOICEVOX does per accent-phrase group. */
    private class FakeSpeaker(private val result: Synthesized, var chunks: Int = 1) : Speaker {
        val spoken = ArrayList<Pair<String, Float>>()

        var lastRtf = -1f

        override fun synthesize(text: String, speedScale: Float, rtf: Float, emit: (Synthesized) -> Unit) {
            spoken += text to speedScale
            lastRtf = rtf
            repeat(chunks) { emit(result) }
        }

        override fun release() = Unit
    }

    private class FakeRecogniser : Recogniser {
        val pending = ArrayDeque<Recognised>()

        override fun feed(samples: FloatArray): List<Recognised> = listOfNotNull(pending.removeFirstOrNull())

        override fun reset() = Unit

        override fun release() = Unit
    }

    private class FakeMic : MicSource {
        var started = 0
        var stopped = 0

        override fun start() {
            started++
        }

        override fun stop() {
            stopped++
        }
    }

    /** Like the real monitor: the next anchor is now + latency + whatever is still queued from earlier plays. */
    private class FakeMonitor(private val nowNanos: () -> Long) : Monitor {
        val played = ArrayList<Pcm16>()
        var addedBeforePlay: Boolean? = null
        var onPlay: (() -> Unit)? = null
        private var queuedUntilNanos = 0L

        override fun nextAnchorNanos(): Long = maxOf(nowNanos() + 80_000_000L, queuedUntilNanos)

        override fun play(pcm: Pcm16) {
            onPlay?.invoke()
            played += pcm
            queuedUntilNanos = nextAnchorNanos() + (pcm.durationSeconds * 1_000_000_000L).toLong()
        }

        override fun stop() = Unit
    }

    private class FakeSink(override val sampleRate: Int = 44_100, override val stereo: Boolean = true) : PcmSink {
        override val latencyMs = 100
        override var consuming = true
        override var queuedMs = 0
        val writes = ArrayList<Int>()
        var acceptPerWrite = Int.MAX_VALUE

        override fun write(pcm: ByteArray, offset: Int, size: Int): Int {
            val accepted = minOf(size, acceptPerWrite)
            writes += accepted
            return accepted
        }
    }

    private class Harness(scope: TestScope, modelsFailFirst: Boolean = false) {
        var nowMs = 10_000L
        val speaker = FakeSpeaker(
            Synthesized(
                Pcm16(ShortArray(24_000), 24_000, 1),
                UtteranceTiming(listOf(PhraseTiming(listOf(MoraTiming(null, "a", 1f)), null)), 0f, 0f),
                "あ。",
            ),
        )
        val recogniser = FakeRecogniser()
        val mic = FakeMic()
        val monitor = FakeMonitor { nowMs * 1_000_000L }
        var micChunkSink: ((FloatArray) -> Unit)? = null
        var modelFailures = if (modelsFailFirst) 1 else 0
        val logs = ArrayList<String>()
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val controller = ZundamonVoiceController(
            support = VoiceSupport(supported = true),
            models = {
                if (modelFailures > 0) {
                    modelFailures--
                    throw IllegalStateException("HTTP 503")
                }
                VoicePaths("enc", "dec", "join", "tok", "vad", "vvm", "dict")
            },
            recogniserFactory = { recogniser },
            speakerFactory = { speaker },
            micFactory = { onChunk ->
                micChunkSink = onChunk
                mic
            },
            monitorFactory = { monitor },
            uptimeMs = { nowMs },
            mainDispatcher = dispatcher,
            asrDispatcher = dispatcher,
            ttsDispatcher = dispatcher,
            log = { logs += it },
        )
    }

    @Test
    fun say_speaksThroughTheMonitor_schedulesLipsBeforeTheWrite_andReleasesTheMouthAfterwards() =
        runTest(timeout = 5.seconds) {
            val h = Harness(this)
            h.monitor.onPlay =
                {
                    h.monitor.addedBeforePlay =
                        h.controller.active.value &&
                        h.controller.sample(h.nowMs * 1_000_000L + 100_000_000L).aa > 0f
                }
            h.controller.say("あいう")
            runCurrent()
            assertEquals(listOf("あいう。" to 1f), h.speaker.spoken)
            assertEquals(1, h.monitor.played.size)
            assertEquals(
                true,
                h.monitor.addedBeforePlay,
                "the viseme track must be on the timeline before the blocking play",
            )
            assertEquals(VoiceState.SPEAKING, h.controller.state.value)
            assertTrue(h.controller.active.value)
            // 0.5 s in: the mouth is open on 「あ」.
            assertTrue(h.controller.sample(h.nowMs * 1_000_000L + 580_000_000L).aa > 0.9f)
            // Let the utterance finish (1 s + 80 ms latency) → IDLE, mouth released, no leftover lag.
            h.nowMs += 1_200
            advanceTimeBy(1_300)
            runCurrent()
            assertEquals(VoiceState.IDLE, h.controller.state.value)
            assertFalse(h.controller.active.value, "active must drop once nothing is left to speak")
            assertEquals(0f, h.controller.lagSeconds.value)
            h.controller.stop()
        }

    @Test
    fun chunkedUtterance_playsEveryChunk_andTheLipsFollowChunkAfterChunk() = runTest(timeout = 5.seconds) {
        val h = Harness(this)
        h.speaker.chunks = 2 // two 1 s chunks
        h.controller.say("あいうえおかきくけこ")
        runCurrent()
        assertEquals(2, h.monitor.played.size)
        val start = h.nowMs * 1_000_000L + 80_000_000L
        assertTrue(h.controller.sample(start + 500_000_000L).aa > 0.9f) // chunk 1 open
        assertTrue(h.controller.sample(start + 1_500_000_000L).aa > 0.9f) // chunk 2 open, after chunk 1
        assertEquals(VoiceState.SPEAKING, h.controller.state.value)
        assertEquals(2f, h.controller.lagSeconds.value, 0.15f)
        h.nowMs += 2_300
        advanceTimeBy(2_400)
        runCurrent()
        assertEquals(VoiceState.IDLE, h.controller.state.value)
        assertFalse(h.controller.active.value)
        h.controller.stop()
    }

    @Test
    fun zeroChunkUtterance_stillCompletes_andReleasesTheMouth() = runTest(timeout = 5.seconds) {
        val h = Harness(this)
        h.speaker.chunks = 0 // e.g. Open JTalk found no accent phrases
        h.controller.say("……。")
        runCurrent()
        assertEquals(VoiceState.IDLE, h.controller.state.value)
        assertFalse(h.controller.active.value)
        h.controller.stop()
    }

    @Test
    fun stopDuringSynthesis_dropsTheUtterance_andNothingPlaysAfterwards() = runTest(timeout = 5.seconds) {
        val h = Harness(this)
        h.monitor.onPlay = { error("must not play into a stopped session") }
        lateinit var controller: ZundamonVoiceController
        // Emitting a chunk stops the session from inside synthesis (as if the user hit Stop mid-way).
        val stopping = object : Speaker {
            override fun synthesize(text: String, speedScale: Float, rtf: Float, emit: (Synthesized) -> Unit) {
                controller.stop()
                emit(Synthesized(Pcm16(ShortArray(24_000), 24_000, 1), UtteranceTiming(emptyList(), 1f, 0f), text))
            }

            override fun release() = Unit
        }
        controller = ZundamonVoiceController(
            support = VoiceSupport(supported = true),
            models = { VoicePaths("enc", "dec", "join", "tok", "vad", "vvm", "dict") },
            recogniserFactory = { h.recogniser },
            speakerFactory = { stopping },
            micFactory = { h.mic },
            monitorFactory = { h.monitor },
            uptimeMs = { h.nowMs },
            mainDispatcher = h.dispatcher,
            asrDispatcher = h.dispatcher,
            ttsDispatcher = h.dispatcher,
            log = { h.logs += it },
        )
        controller.say("あいう")
        runCurrent()
        assertEquals(VoiceState.IDLE, controller.state.value)
        assertFalse(controller.active.value)
        assertTrue(h.monitor.played.isEmpty())
        controller.stop()
    }

    @Test
    fun modelFailure_isReportedAsError_andTheNextStartRecovers() = runTest(timeout = 5.seconds) {
        val h = Harness(this, modelsFailFirst = true)
        h.controller.start()
        runCurrent()
        assertEquals(VoiceState.ERROR, h.controller.state.value)
        assertNotNull(h.controller.error.value)
        assertFalse(h.controller.active.value, "a failed start must not leave the avatar's mouth captured")
        assertEquals(0, h.mic.started)

        h.controller.start() // second attempt: models now available
        runCurrent()
        assertEquals(VoiceState.LISTENING, h.controller.state.value)
        assertEquals(null, h.controller.error.value)
        assertEquals(1, h.mic.started)
        assertTrue(h.controller.active.value)
        h.controller.stop()
    }

    @Test
    fun recognisedSpeech_isNormalisedAndSpoken_andSegmentsHeardDuringPlaybackAreDropped() =
        runTest(timeout = 5.seconds) {
            val h = Harness(this)
            h.controller.start()
            runCurrent()
            assertEquals(VoiceState.LISTENING, h.controller.state.value)
            val feed = assertNotNull(h.micChunkSink)
            h.recogniser.pending += Recognised("こんにちは", audioSeconds = 1.5f, decodeMs = 100)
            feed(FloatArray(512))
            runCurrent()
            assertEquals("こんにちは。", h.controller.lastText.value)
            assertEquals(listOf("こんにちは。" to 1f), h.speaker.spoken)
            assertEquals(VoiceState.SPEAKING, h.controller.state.value)
            // While the monitor plays, a segment that overlaps the playback is discarded (echo gate).
            h.recogniser.pending += Recognised("エコー", audioSeconds = 0.5f, decodeMs = 50)
            h.nowMs += 300
            feed(FloatArray(512))
            runCurrent()
            assertEquals(1, h.speaker.spoken.size)
            assertTrue(h.logs.any { "dropped a segment" in it })
            // After playback + tail, speech is accepted again and the state returns to LISTENING.
            h.nowMs += 2_000
            advanceTimeBy(2_100)
            runCurrent()
            assertEquals(VoiceState.LISTENING, h.controller.state.value)
            h.recogniser.pending += Recognised("次の話", audioSeconds = 0.5f, decodeMs = 50)
            h.nowMs += 1_000
            feed(FloatArray(512))
            runCurrent()
            assertEquals(2, h.speaker.spoken.size)
            h.controller.stop()
            assertEquals(1, h.mic.stopped)
            assertEquals(VoiceState.IDLE, h.controller.state.value)
            assertFalse(h.controller.active.value)
        }

    @Test
    fun broadcastSink_receivesResampledStereo_andLipsAnchorAtWriteTimePlusQueuedAudio() = runTest(timeout = 5.seconds) {
        val h = Harness(this)
        val sink = FakeSink()
        sink.queuedMs = 1_500 // the previous utterance is still in the ring
        h.controller.attachAudioSink(sink)
        assertTrue(h.controller.broadcasting.value)
        h.controller.say("あいう")
        runCurrent()
        // 1 s of 24 kHz mono → 1 s of 44.1 kHz stereo 16-bit = 176 400 bytes.
        assertEquals(176_400, sink.writes.sum())
        assertTrue(h.monitor.played.isEmpty(), "audio must not also go to the speaker while broadcasting")
        val anchorNanos = h.nowMs * 1_000_000L + 1_500L * 1_000_000L
        assertEquals(0f, h.controller.sample(anchorNanos - 1_000_000L).aa, "closed until the queued audio has played")
        assertTrue(h.controller.sample(anchorNanos + 500_000_000L).aa > 0.9f, "open half-way through the utterance")
        h.controller.detachAudioSink()
        assertFalse(h.controller.broadcasting.value)
        h.controller.stop() // the speak loop polls on virtual time; end the session so runTest can idle
    }

    @Test
    fun broadcastSink_notConsumingOffAir_fallsBackToTheMonitor() = runTest(timeout = 5.seconds) {
        val h = Harness(this)
        val sink = FakeSink().apply { consuming = false }
        h.controller.attachAudioSink(sink)
        h.controller.say("あいう")
        runCurrent()
        assertTrue(sink.writes.isEmpty())
        assertEquals(1, h.monitor.played.size)
        h.controller.stop()
    }

    @Test
    fun muted_dropsQueuedSpeech() = runTest(timeout = 5.seconds) {
        val h = Harness(this)
        h.controller.setMuted(true)
        h.controller.say("あいう")
        runCurrent()
        assertTrue(h.speaker.spoken.isEmpty())
        assertTrue(h.controller.muted.value)
        h.controller.stop()
    }
}
