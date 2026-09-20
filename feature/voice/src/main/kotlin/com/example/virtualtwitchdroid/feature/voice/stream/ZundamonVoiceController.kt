package com.example.virtualtwitchdroid.feature.voice.stream

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.virtualtwitchdroid.core.common.media.MouthTrackSource
import com.example.virtualtwitchdroid.core.common.media.PcmSink
import com.example.virtualtwitchdroid.core.common.media.SpeechStreamSource
import com.example.virtualtwitchdroid.core.common.media.VisemeFrame
import com.example.virtualtwitchdroid.core.common.media.VoiceState
import com.example.virtualtwitchdroid.feature.voice.VoiceSupport
import com.example.virtualtwitchdroid.feature.voice.asr.JapaneseRecogniser
import com.example.virtualtwitchdroid.feature.voice.asr.ListenPolicy
import com.example.virtualtwitchdroid.feature.voice.asr.MicCapture
import com.example.virtualtwitchdroid.feature.voice.asr.MicSource
import com.example.virtualtwitchdroid.feature.voice.asr.Recogniser
import com.example.virtualtwitchdroid.feature.voice.assets.ModelStore
import com.example.virtualtwitchdroid.feature.voice.assets.VoicePaths
import com.example.virtualtwitchdroid.feature.voice.audio.Monitor
import com.example.virtualtwitchdroid.feature.voice.audio.MonitorPlayer
import com.example.virtualtwitchdroid.feature.voice.audio.Resampler
import com.example.virtualtwitchdroid.feature.voice.lipsync.VisemeTimeline
import com.example.virtualtwitchdroid.feature.voice.lipsync.VisemeTrack
import com.example.virtualtwitchdroid.feature.voice.text.TextNormaliser
import com.example.virtualtwitchdroid.feature.voice.tts.RtfEstimator
import com.example.virtualtwitchdroid.feature.voice.tts.Speaker
import com.example.virtualtwitchdroid.feature.voice.tts.Synthesized
import com.example.virtualtwitchdroid.feature.voice.tts.UtteranceQueue
import com.example.virtualtwitchdroid.feature.voice.tts.VoicevoxSpeaker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Zundamon voice pipeline: `MicSource → Silero VAD + ReazonSpeech Zipformer → TextNormaliser →
 * UtteranceQueue → VOICEVOX → (PcmSink | Monitor) + VisemeTimeline`. One instance for the app
 * ([SpeechStreamSource] for the publisher and the Avatar tab, [MouthTrackSource] for the renderer).
 *
 * Threads: the microphone has its own thread and hands chunks to a bounded channel; recognition runs on the
 * ASR dispatcher, synthesis + delivery on the TTS dispatcher (both engines are single-consumer; VOICEVOX is
 * capped at 4 native threads, the Zipformer at 2); the playback worker holds the blocking audio writes so the
 * next chunk synthesises while the current one plays. Public methods are main-thread calls that flip state and
 * launch work; failures are marshalled back to main before they touch the session fields.
 *
 * Everything Android-specific (model store, engines, microphone, speaker, clock, log) is injected so the
 * state machine is unit-tested with fakes (`ZundamonVoiceControllerTest`).
 */
@Singleton
class ZundamonVoiceController internal constructor(
    private val support: VoiceSupport,
    private val models: suspend () -> VoicePaths,
    private val recogniserFactory: (VoicePaths) -> Recogniser,
    private val speakerFactory: (VoicePaths) -> Speaker,
    private val micFactory: (onChunk: (FloatArray) -> Unit) -> MicSource,
    private val monitorFactory: (sampleRate: Int) -> Monitor,
    private val uptimeMs: () -> Long,
    private val mainDispatcher: CoroutineDispatcher,
    private val asrDispatcher: CoroutineDispatcher,
    private val ttsDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit,
    private val playDispatcher: CoroutineDispatcher = ttsDispatcher,
) : SpeechStreamSource,
    MouthTrackSource {

    @Inject
    constructor(@ApplicationContext context: Context, support: VoiceSupport) : this(
        support = support,
        models = { withContext(Dispatchers.IO) { ModelStore(context).ensure() } },
        recogniserFactory = { JapaneseRecogniser(it) },
        speakerFactory = { VoicevoxSpeaker(it) },
        micFactory = { MicCapture(it) },
        monitorFactory = { MonitorPlayer(it) },
        uptimeMs = SystemClock::uptimeMillis,
        mainDispatcher = Dispatchers.Main.immediate,
        asrDispatcher = Executors.newSingleThreadExecutor { Thread(it, "zundamon-asr") }.asCoroutineDispatcher(),
        ttsDispatcher = Executors.newSingleThreadExecutor { Thread(it, "zundamon-tts") }.asCoroutineDispatcher(),
        log = { Log.i(TAG, it) },
        playDispatcher = Executors.newSingleThreadExecutor { Thread(it, "zundamon-play") }.asCoroutineDispatcher(),
    )

    override val supported: Boolean get() = support.supported

    private val _state = MutableStateFlow(VoiceState.IDLE)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    private val _lastText = MutableStateFlow<String?>(null)
    override val lastText: StateFlow<String?> = _lastText.asStateFlow()
    private val _lagSeconds = MutableStateFlow(0f)
    override val lagSeconds: StateFlow<Float> = _lagSeconds.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    private val _muted = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _active = MutableStateFlow(false)
    override val active: StateFlow<Boolean> = _active.asStateFlow()
    private val _broadcasting = MutableStateFlow(false)
    override val broadcasting: StateFlow<Boolean> = _broadcasting.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val timeline = VisemeTimeline()
    private val queue = UtteranceQueue()
    private val listenPolicy = ListenPolicy()
    private val texts = Channel<Unit>(Channel.UNLIMITED)
    private val chunks = Channel<FloatArray>(capacity = CHUNK_QUEUE)

    // Engines are loaded once and kept warm across start/stop; models are verified/downloaded on first use.
    private var paths: VoicePaths? = null
    private var recogniser: Recogniser? = null
    private var speaker: Speaker? = null

    @Volatile private var sink: PcmSink? = null

    @Volatile private var monitor: Monitor? = null
    private var mic: MicSource? = null
    private var listenJob: Job? = null
    private var speakJob: Job? = null
    private var playJob: Job? = null
    private var prepareJob: Job? = null
    private val playQueue = Channel<Playable>(Channel.UNLIMITED)
    private val rtf = RtfEstimator()

    /** Bumped by stop(); playback items carry the epoch they were queued under. */
    @Volatile private var epoch = 0

    override fun start() {
        if (!supported || listenJob != null) return
        if (_state.value == VoiceState.ERROR) { // a previous failure (e.g. download) is retried from scratch
            _state.value = VoiceState.IDLE
            _error.value = null
        }
        _active.value = true
        val job = scope.launch {
            val ready = prepare() ?: return@launch
            ensureSpeakLoop()
            _state.value = VoiceState.LISTENING
            listen(ready)
        }
        listenJob = job
        job.invokeOnCompletion { cause ->
            // Whatever ended the session (normal exit, failure, cancel): forget the job on main so the next
            // start() is never refused, and release the mouth if nothing is left to speak.
            scope.launch {
                if (listenJob === job) {
                    listenJob = null
                    val idle = !timeline.isSpeaking(nowNanos()) && queue.size == 0
                    if (idle) _active.value = false
                }
                if (cause != null && cause !is kotlinx.coroutines.CancellationException) {
                    fail("voice pipeline failed", cause)
                }
            }
        }
    }

    /** End the listener and drop any microphone chunks it left behind. */
    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        mic?.stop()
        mic = null
        while (chunks.tryReceive().isSuccess) Unit
    }

    /** Our microphone → Silero VAD + Zipformer on the ASR thread; each segment goes through [accept]. */
    private suspend fun listen(ready: VoicePaths) {
        val capture = micFactory { chunk -> chunks.trySend(chunk) }
        try {
            capture.start()
        } catch (e: RuntimeException) {
            fail("microphone unavailable", e)
            return
        }
        mic = capture
        withContext(asrDispatcher) {
            val rec = recogniser ?: recogniserFactory(ready).also { recogniser = it }
            rec.reset()
            for (chunk in chunks) {
                if (_muted.value) continue
                for (segment in rec.feed(chunk)) {
                    val endMs = uptimeMs()
                    accept(segment.text, endMs - (segment.audioSeconds * MILLIS_PER_SECOND).toLong(), endMs)
                }
            }
        }
    }

    /** The echo gate, normalisation and the hand-off to the TTS queue. */
    private fun accept(raw: String, startMs: Long, endMs: Long) {
        if (!listenPolicy.accepts(startMs, endMs)) {
            log("dropped a segment heard while the monitor was speaking: $raw")
            return
        }
        val text = TextNormaliser.normalise(raw) ?: return
        _lastText.value = text
        queue.offer(text)
        texts.trySend(Unit)
    }

    override fun stop() {
        stopListening()
        speakJob?.cancel()
        speakJob = null
        playJob?.cancel()
        playJob = null
        epoch++ // anything still queued for playback belongs to the old session and is dropped
        queue.clear()
        while (texts.tryReceive().isSuccess) Unit
        while (playQueue.tryReceive().getOrNull()?.also { it.done?.complete(Unit) } != null) Unit
        timeline.clear()
        listenPolicy.reset()
        monitor?.stop()
        monitor = null
        _lagSeconds.value = 0f
        _active.value = false
        if (_state.value != VoiceState.ERROR) _state.value = VoiceState.IDLE
        log("stopped")
    }

    override fun say(text: String) {
        if (!supported) return
        val normalised = TextNormaliser.normalise(text) ?: return
        scope.launch {
            prepare() ?: return@launch
            ensureSpeakLoop()
            _active.value = true
            if (listenJob == null && _state.value != VoiceState.SPEAKING) _state.value = VoiceState.THINKING
            _lastText.value = normalised
            queue.offer(normalised)
            texts.trySend(Unit)
        }
    }

    override fun setMuted(muted: Boolean) {
        _muted.value = muted
        if (muted) {
            queue.clear()
            timeline.clear()
        }
    }

    override fun attachAudioSink(sink: PcmSink) {
        this.sink = sink
        _broadcasting.value = true
        monitor?.stop()
        monitor = null
        log("audio sink attached (latency ${sink.latencyMs} ms; the format is read at delivery time)")
    }

    override fun detachAudioSink() {
        sink = null
        _broadcasting.value = false
        log("audio sink detached")
    }

    override fun sample(uptimeNanos: Long): VisemeFrame = timeline.sample(uptimeNanos)

    /** Models present + engines constructed; null (with the ERROR state set) when that is impossible. */
    private suspend fun prepare(): VoicePaths? {
        paths?.let { return it }
        prepareJob?.join()
        paths?.let { return it }
        if (_state.value == VoiceState.ERROR) return null // a concurrent prepare just failed; start() retries
        _state.value = VoiceState.PREPARING
        val job = scope.launch {
            val ready = runCatching { models() }.getOrElse {
                fail("voice models unavailable", it)
                return@launch
            }
            val built = withContext(ttsDispatcher) { runCatching { speaker ?: speakerFactory(ready) } }
                .getOrElse {
                    fail("VOICEVOX failed to load", it)
                    return@launch
                }
            speaker = built
            paths = ready
        }
        prepareJob = job
        job.join()
        prepareJob = null
        return paths
    }

    /**
     * The TTS producer: synthesises queued texts chunk by chunk ([ChunkPlan] decides, from the device's
     * measured [RtfEstimator] and the phrase pauses, whether a split can be played back gap-free) and hands
     * every chunk to the playback worker; then holds SPEAKING until the utterance has been heard.
     */
    private fun ensureSpeakLoop() {
        if (speakJob == null) {
            speakJob = scope.launch(ttsDispatcher) {
                for (tick in texts) {
                    val text = queue.poll() ?: continue
                    val speaker = speaker ?: continue
                    if (_muted.value) continue
                    _state.value = VoiceState.THINKING
                    val speed = UtteranceQueue.speedScaleFor(timeline.remainingSeconds(nowNanos()))
                    val utteranceEpoch = epoch
                    val job = coroutineContext[Job]
                    val done = CompletableDeferred<Unit>()
                    var spokenSeconds = 0f
                    val result = runCatching {
                        speaker.synthesize(text, speed, rtf.estimate) { chunk ->
                            job?.ensureActive() // stop() while synthesising: never play into a torn-down session
                            rtf.update(chunk.synthesisSeconds, chunk.pcm.durationSeconds)
                            val track = VisemeTrack.build(chunk.timing)
                            spokenSeconds += track.durationSeconds
                            playQueue.trySend(Playable(chunk, track, utteranceEpoch, null))
                        }
                    }
                    result.exceptionOrNull()?.let { e ->
                        if (e is CancellationException) throw e
                        log("synthesis failed for '$text': $e")
                    }
                    playQueue.trySend(Playable(null, null, utteranceEpoch, done)) // end-of-utterance marker
                    done.await()
                    // Hold SPEAKING until this utterance is heard, unless the next text is already waiting.
                    // Bounded by the utterance's own length (+ a margin) so a stalled clock can never spin this forever.
                    var polls = (spokenSeconds * MILLIS_PER_SECOND / POLL_MS).toInt() + EXTRA_POLLS
                    while (polls-- > 0 && timeline.isSpeaking(nowNanos()) && queue.size == 0) delay(POLL_MS)
                    if (!timeline.isSpeaking(nowNanos())) listenPolicy.monitorSpeaking(false, uptimeMs())
                    _lagSeconds.value = timeline.remainingSeconds(nowNanos())
                    if (queue.size == 0) {
                        _state.value = if (listenJob != null) VoiceState.LISTENING else VoiceState.IDLE
                        if (listenJob == null && !timeline.isSpeaking(nowNanos())) _active.value = false
                    }
                }
            }
        }
        if (playJob == null) {
            // The playback worker: blocking writes (sink ring or AudioTrack) live here so synthesis of the next
            // chunk overlaps playback of the current one — the whole point of chunking.
            playJob = scope.launch(playDispatcher) {
                for (p in playQueue) {
                    if (p.done != null) {
                        p.done.complete(Unit)
                        continue
                    }
                    if (p.epoch != epoch) continue // queued before a stop(): drop silently
                    val chunk = p.chunk ?: continue
                    val track = p.track ?: continue
                    _state.value = VoiceState.SPEAKING
                    val broadcast = sink?.takeIf { it.consuming }
                    if (broadcast != null) {
                        deliver(chunk.pcm.samples, chunk.pcm.sampleRate, broadcast, track)
                    } else {
                        // Off-air the broadcast source is not draining (RootEncoder starts it with the stream), so
                        // the streamer hears the rehearsal through the monitor — lips scheduled BEFORE the blocking
                        // write, recognition gated while it plays. The monitor is created and used only here.
                        val player = monitor ?: monitorFactory(chunk.pcm.sampleRate).also { monitor = it }
                        listenPolicy.monitorSpeaking(true, uptimeMs())
                        timeline.add(track, player.nextAnchorNanos())
                        player.play(chunk.pcm)
                    }
                    _lagSeconds.value = timeline.remainingSeconds(nowNanos())
                }
            }
        }
    }

    /** One item for the playback worker: a synthesised chunk, or (with [done]) the end of an utterance. */
    private class Playable(
        val chunk: Synthesized?,
        val track: VisemeTrack?,
        val epoch: Int,
        val done: CompletableDeferred<Unit>?,
    )

    /**
     * Resamples to the sink's format and writes it in slices, pacing on how much the sink accepts. The lips
     * are anchored at the first accepted write plus whatever the sink still had queued: RootEncoder's ring
     * encodes a byte written at uptime W with timestamp ≈ W, so no output latency is added.
     */
    private fun deliver(mono: ShortArray, fromRate: Int, sink: PcmSink, track: VisemeTrack) {
        val bytes = Resampler.toPcm16Bytes(mono, fromRate, sink.sampleRate, sink.stereo)
        log(
            "delivering ${bytes.size} bytes to the broadcast " +
                "(${sink.sampleRate} Hz, stereo=${sink.stereo}, queued ${sink.queuedMs} ms)",
        )
        var offset = 0
        var anchored = false
        var stalls = EXTRA_POLLS
        while (offset < bytes.size) {
            val queuedBefore = sink.queuedMs
            val accepted = sink.write(bytes, offset, minOf(SLICE_BYTES, bytes.size - offset))
            if (accepted > 0 && !anchored) {
                anchored = true
                timeline.add(track, nowNanos() + queuedBefore * NANOS_PER_MILLI)
            }
            offset += accepted
            if (accepted <= 0) { // the sink's ring is full: let it drain — but never wait forever
                if (--stalls < 0) break
                Thread.sleep(POLL_MS) // blocking is fine: this is the playback worker's own thread
            }
            if (!sink.consuming && offset < bytes.size) break // the stream stopped mid-utterance
        }
    }

    private fun nowNanos() = uptimeMs() * NANOS_PER_MILLI

    /** Record the failure and tear the session down — on main, whichever thread noticed it. */
    private fun fail(message: String, e: Throwable?) {
        scope.launch {
            log(if (e != null) "$message: $e" else message)
            _error.value = e?.message?.let { "$message: $it" } ?: message
            _state.value = VoiceState.ERROR
            stop()
        }
    }

    private companion object {
        const val TAG = "ZundamonVoice"
        const val CHUNK_QUEUE = 64
        const val SLICE_BYTES = 8_192
        const val POLL_MS = 20L

        /** Extra polls (≈ 5 s) tolerated beyond an utterance's length, or while a sink refuses bytes. */
        const val EXTRA_POLLS = 250
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000f
    }
}
