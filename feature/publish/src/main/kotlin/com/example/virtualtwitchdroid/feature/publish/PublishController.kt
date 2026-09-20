package com.example.virtualtwitchdroid.feature.publish

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.view.TextureView
import androidx.annotation.StringRes
import com.example.virtualtwitchdroid.core.common.media.AvatarStreamSource
import com.example.virtualtwitchdroid.core.common.media.SpeechStreamSource
import com.example.virtualtwitchdroid.core.data.repository.ChatRepository
import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.ChatMessage
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.generic.GenericStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The channel to receive chat from: [channel] only while [live] and non-blank, else null (socket off). */
internal fun chatChannelWhenLive(live: Boolean, channel: String?): String? =
    if (live && !channel.isNullOrBlank()) channel else null

/** Fold a chat event into the rolling message list (newest last, capped at [max]); non-messages pass through. */
internal fun reduceChat(acc: List<ChatMessage>, event: ChatEvent, max: Int): List<ChatMessage> =
    if (event is ChatEvent.Message) (acc + event.message).takeLast(max) else acc

/**
 * App-scoped owner of the RootEncoder camera [GenericStream] and the broadcast state, hoisted out of a
 * ViewModel so the camera can keep running in the floating mini-player after the user leaves the
 * Publish screen (mirrors [com.example.virtualtwitchdroid.feature.stream.PlayerController]).
 *
 * RootEncoder replaces StreamPack here: ONE always-running GL pipeline that the fullscreen and the
 * mini attach/detach a preview to freely (`startPreview`/`stopPreview`), so swapping preview targets
 * never reconfigures the camera (no black flash) and each preview scales via `getGlInterface()` — the
 * same renderer works off-air AND while live, so there is no off-air/live preview split.
 *
 * Camera lifecycle (so the camera is never left running needlessly): the stream exists only while the
 * Publish screen is on-screen **or** the camera is floating (minimized). Leaving the screen without
 * minimizing — or closing the float — releases it.
 *
 * **VTuber mode** ([vtuberMode]) swaps the camera for the app's face-tracked avatar as the video source
 * ([AvatarVideoSource] over [AvatarStreamSource]) — the same GL pipeline, preview swap and broadcast,
 * just a different producer, so it can be toggled off-air or live. Hidden on devices the avatar does not
 * support ([avatarSupported]).
 *
 * Broadcast state is driven by RootEncoder's [ConnectChecker] callbacks (this class implements it).
 */
@Singleton
class PublishController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val avatarSource: AvatarStreamSource,
    private val voiceSource: SpeechStreamSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _streamer = MutableStateFlow<GenericStream?>(null)
    val streamer: StateFlow<GenericStream?> = _streamer.asStateFlow()

    private val _uiState = MutableStateFlow<PublishUiState>(PublishUiState.Idle)
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    /** True while the camera is docked in the floating mini-player. */
    private val _minimized = MutableStateFlow(false)
    val minimized: StateFlow<Boolean> = _minimized.asStateFlow()

    /** True while the fullscreen Publish screen is on-screen. */
    private val _screenActive = MutableStateFlow(false)
    val screenActive: StateFlow<Boolean> = _screenActive.asStateFlow()

    /** Publish encode resolution options (PORTRAIT width×height) — the camera analogue of the watch
     * player's quality menu. `width` is the short side (the "p" number). */
    enum class VideoQuality(@StringRes val labelRes: Int, val width: Int, val height: Int) {
        P1080(R.string.quality_1080, 1080, 1920),
        P720(R.string.quality_720, 720, 1280),
        P480(R.string.quality_480, 480, 848), // 848 (16-aligned) not 854 — some HW encoders reject non-16 dims
        P360(R.string.quality_360, 360, 640),
    }

    private val _quality = MutableStateFlow(VideoQuality.P720)
    val quality: StateFlow<VideoQuality> = _quality.asStateFlow()

    /** True while the microphone is muted (no audio in the broadcast). Persists across re-init. */
    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    /** True while the avatar (not the camera) is the broadcast video. Persists across re-init. */
    private val _vtuberMode = MutableStateFlow(false)
    val vtuberMode: StateFlow<Boolean> = _vtuberMode.asStateFlow()

    /** Whether this device can run the avatar at all — gates the VTuber toggle. */
    val avatarSupported: Boolean get() = avatarSource.supported

    /**
     * Zundamon voice: the broadcast audio is VOICEVOX ずんだもん speaking the streamer's recognised Japanese
     * instead of the microphone (VTuber mode only). Persists across re-init like [vtuberMode].
     */
    private val _zundamonVoice = MutableStateFlow(false)
    val zundamonVoice: StateFlow<Boolean> = _zundamonVoice.asStateFlow()

    /** Whether this device can run the voice — gates the toggle. */
    val voiceSupported: Boolean get() = voiceSource.supported

    /** The voice pipeline's state, recognised text and lag for the overlay. */
    val voice: SpeechStreamSource get() = voiceSource

    /**
     * Route the broadcast audio through the Zundamon voice ([enabled]) or the microphone. With a running
     * stream this swaps RootEncoder's audio source in place (`changeAudioSource`, live or not) under the
     * stream lock, then starts/stops the voice pipeline — which owns the microphone in this mode.
     */
    fun setZundamonVoice(enabled: Boolean) {
        if (enabled && (!voiceSupported || !_vtuberMode.value)) return
        if (_zundamonVoice.value == enabled) return
        val stream = _streamer.value
        if (stream == null) {
            _zundamonVoice.value = enabled // applied on the next initialize()
            return
        }
        scope.launch {
            val ok = streamMutex.withLock {
                withContext(Dispatchers.Default) {
                    runCatching { stream.changeAudioSource(audioSourceFor(enabled)) }.isSuccess
                }
            }
            if (!ok) {
                voiceSource.detachAudioSink()
                return@launch
            }
            _zundamonVoice.value = enabled
            if (enabled) {
                voiceSource.setMuted(_micMuted.value)
                voiceSource.start()
            } else {
                voiceSource.stop()
                // Mute state carries over to the fresh microphone source.
                if (_micMuted.value) micSource(stream)?.mute()
            }
        }
    }

    /** The audio producer for [zundamon] mode: the voice's sink (attached here), or a fresh microphone. */
    private fun audioSourceFor(zundamon: Boolean): AudioSource = if (zundamon) {
        ZundamonAudioSource().also { voiceSource.attachAudioSink(it.sink) }
    } else {
        voiceSource.detachAudioSink()
        MicrophoneSource()
    }

    // A source swap is a blocking camera close/open off the main thread; ignore taps until it settles.
    private var switchingSource = false

    /**
     * Serializes every off-main mutation of the [GenericStream] (source swap, camera flip, quality
     * re-prepare, teardown). RootEncoder's `changeVideoSource` is a multi-step stop/clear/start/release
     * sequence, not atomic: a teardown or re-prepare landing in the middle would release the old source
     * while the avatar is being started — leaving a session nobody detaches — or start the new source on a
     * stopped GL thread. Each mutation takes the lock for its whole sequence.
     */
    private val streamMutex = Mutex()

    /**
     * Use the avatar ([enabled]) or the camera as the video source. With a running stream this swaps the
     * source in place via `changeVideoSource` (RootEncoder stops the old source, starts the new one on
     * the same GL texture — live or not, no reconnect); without one the choice applies on [initialize].
     * Runs OFF the main thread like [switchCamera]: closing the Camera2 device blocks.
     */
    fun setVtuberMode(enabled: Boolean) {
        if (enabled && !avatarSupported) return
        if (_vtuberMode.value == enabled || switchingSource) return
        if (!enabled) setZundamonVoice(false) // the voice only exists inside VTuber mode
        val stream = _streamer.value
        if (stream == null) {
            _vtuberMode.value = enabled // applied on the next initialize()
            return
        }
        switchingSource = true
        scope.launch {
            val ok = streamMutex.withLock {
                withContext(Dispatchers.Default) {
                    runCatching {
                        stream.changeVideoSource(videoSourceFor(enabled))
                        // A swap resets the GL orientation config to the source default (display-derived);
                        // re-assert the fixed portrait rotation the fullscreen relies on (see attachPreview).
                        if (!_minimized.value) stream.setOrientation(FULLSCREEN_CAMERA_ROTATION)
                    }.isSuccess
                }
            }
            switchingSource = false
            if (ok) _vtuberMode.value = enabled
        }
    }

    /** The video producer for [vtuber] mode: the app's avatar, or a fresh Camera2 device. */
    private fun videoSourceFor(vtuber: Boolean): VideoSource =
        if (vtuber) AvatarVideoSource(avatarSource) else Camera2Source(context)

    /** The channel login whose chat to receive while live (the username typed on the Publish screen). */
    private val targetChannel = MutableStateFlow<String?>(null)

    /** Set the channel to receive chat from; blank clears it. */
    fun setChatChannel(login: String?) {
        targetChannel.value = login?.trim()?.lowercase()?.ifBlank { null }
    }

    /**
     * Live chat received for [targetChannel] — but ONLY while broadcasting (Live): a fresh anonymous IRC
     * socket opens when we go live and is torn down when we stop / leave (WhileSubscribed). Off-air it
     * stays empty and opens no socket. Socket failures are swallowed (the overlay just stops updating).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessage>> =
        combine(uiState, targetChannel) { state, channel ->
            chatChannelWhenLive(state is PublishUiState.Live, channel)
        }
            .distinctUntilChanged()
            .flatMapLatest { channel ->
                if (channel == null) {
                    flowOf(emptyList<ChatMessage>())
                } else {
                    chatRepository.observeChat(channel)
                        .catch { emit(ChatEvent.Disconnected) }
                        .scan(emptyList<ChatMessage>()) { acc, event -> reduceChat(acc, event, MAX_CHAT_MESSAGES) }
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Broadcast reconnection: remember where we're publishing + whether the user still wants to be
    // live, so an *unintended* connection drop triggers a backoff retry (vs. a user-requested stop).
    // Touched only on the Main dispatcher (scope is Main.immediate; ConnectChecker callbacks are
    // re-posted to it), so they need no extra synchronization.
    private val reconnectPolicy = ReconnectPolicy()
    private var ingestUrl: String? = null
    private var intendStreaming = false
    private var stopping = false
    private var reconnectJob: Job? = null

    // Camera teardown is fire-and-forget off the main thread; a fast leave-then-return (leave the
    // Publish screen without minimizing ⇒ releaseCamera, then reopen the Go Live tab ⇒ initialize)
    // would otherwise open a new Camera2 device while the previous release() is still closing the same
    // device. [initialize] joins this job before constructing the new stream so the two never overlap.
    private var teardownJob: Job? = null
    private var initializing = false

    /** The fullscreen Publish screen reports its visibility so we know when to free the camera. */
    fun setScreenActive(active: Boolean) {
        _screenActive.value = active
        if (active) {
            // Showing the fullscreen screen ⇒ the camera is not floating (reverse an in-app PiP when
            // the user returns to the Go Live tab).
            _minimized.value = false
        } else if (!_minimized.value) {
            releaseCamera()
        }
    }

    @SuppressLint("MissingPermission")
    fun initialize() {
        if (_streamer.value != null || initializing) return
        initializing = true
        scope.launch {
            // Wait for any in-flight camera teardown to finish so we never open the Camera2 device
            // while the previous stream's release() is still closing it.
            teardownJob?.join()
            val stream = runCatching {
                // The plain constructor is Camera2 + microphone; VTuber mode substitutes the avatar, and the
                // Zundamon voice substitutes the microphone.
                val zundamon = _vtuberMode.value && _zundamonVoice.value
                GenericStream(
                    context,
                    connectChecker,
                    videoSourceFor(_vtuberMode.value),
                    audioSourceFor(zundamon),
                ).apply {
                    // 720x1280 @ 4 Mbps H.264 — a PORTRAIT encoder; the upright orientation is applied
                    // per-attach via setOrientation (see attachPreview), independent of this.
                    // PORTRAIT encoder (dims swapped to 720x1280): the camera content is upright
                    // portrait, so a portrait encoder fills it undistorted. A landscape (1280x720)
                    // encoder squished the portrait content (stretched stream). CameraAspect's
                    // constants stay landscape (they drive the mini-window shape, not the encode).
                    val ok = prepareVideo(
                        _quality.value.width,
                        _quality.value.height,
                        VIDEO_BITRATE,
                    ) &&
                        prepareAudio(AUDIO_SAMPLE_RATE, isStereo = true, AUDIO_BITRATE)
                    require(ok) { "Encoder does not support the requested audio/video config" }
                    // RootEncoder's built-in adaptive bitrate: it lowers the video bitrate on network
                    // congestion and recovers as the link clears (replaces StreamPack's regulator).
                    getStreamClient().setBitrateExponentialFactor(BITRATE_ADAPT_FACTOR)
                }
            }.getOrNull()
            initializing = false
            if (stream == null) {
                _uiState.value = PublishUiState.Error(R.string.camera_error)
                return@launch
            }
            // Re-apply the persisted mute state to the freshly created microphone source (or the voice).
            if (_micMuted.value) micSource(stream)?.mute()
            if (_vtuberMode.value && _zundamonVoice.value) {
                voiceSource.setMuted(_micMuted.value)
                voiceSource.start()
            } else {
                // Go Live owns the microphone now: a rehearsal left running on the Avatar tab must not keep a
                // second AudioRecord open (and its speaker output out of the real-mic broadcast).
                voiceSource.stop()
            }
            _streamer.value = stream
            // Clear any prior camera-open error now that we have a working stream (e.g. after Retry).
            if (_uiState.value is PublishUiState.Error) _uiState.value = PublishUiState.Idle
        }
    }

    /** The microphone (default audio source) — [GenericStream]'s audio input is a [MicrophoneSource]. */
    private fun micSource(stream: GenericStream): MicrophoneSource? = stream.audioSource as? MicrophoneSource

    /**
     * Change the publish ENCODE resolution ([q]). The GL render offscreen (= encoder size) is created
     * at `startPreview`, so applying a new size means re-configuring the encoder and cold-restarting
     * the preview (a brief camera reconfigure). Only allowed when NOT broadcasting — changing the
     * encode resolution mid-stream would need a Twitch reconnect; set the quality before going live
     * (the fullscreen hides the quality control while live). The blocking stopPreview/prepareVideo run
     * OFF the main thread (like releaseCamera) so the UI never stalls.
     */
    fun setQuality(q: VideoQuality) {
        if (q == _quality.value) return
        val stream = _streamer.value
        if (stream == null) {
            _quality.value = q // applied on the next initialize()
            return
        }
        if (stream.isStreaming) return // set quality before going live
        val view = currentPreview
        scope.launch {
            val ok = streamMutex.withLock {
                withContext(Dispatchers.Default) {
                    runCatching {
                        if (stream.isOnPreview) stream.stopPreview()
                        stream.prepareVideo(q.width, q.height, VIDEO_BITRATE)
                    }.getOrDefault(false)
                }
            }
            // The preview surface's GL surface is gone after stopPreview; free the tracked Surface.
            previewSurface?.release()
            previewSurface = null
            if (ok) _quality.value = q
            // Cold-restart the GL/camera at the new size on the current preview surface (main thread).
            if (view != null && view.isAvailable) attachPreview(view)
        }
    }

    /** True only when the device exposes BOTH a front and a back camera — gates the switch button. */
    val hasMultipleCameras: Boolean by lazy {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val facings = manager.cameraIdList.mapNotNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.toSet()
            CameraCharacteristics.LENS_FACING_FRONT in facings &&
                CameraCharacteristics.LENS_FACING_BACK in facings
        }.getOrDefault(false)
    }

    /**
     * Flip between the front and back camera on the running GL preview. Works both off-air and while
     * live (RootEncoder swaps only the Camera2 device feeding the existing pipeline, so the broadcast
     * continues with no reconnect).
     *
     * Runs OFF the main thread: `Camera2Source.switchCamera()` synchronously closes then reopens the
     * Camera2 device and blocks on an uninterruptible semaphore until the open callback fires (same
     * reason [setQuality]/[releaseCamera] go off-main). The UI gates this on [hasMultipleCameras], so
     * we never ask for a facing the device lacks (RootEncoder swallows that internally and would leave
     * a dead black preview).
     *
     * In VTuber mode the camera only feeds the face tracker, so the flip switches the tracking lens.
     */
    fun switchCamera() {
        if (_vtuberMode.value) {
            avatarSource.toggleCamera()
            return
        }
        val camera = _streamer.value?.videoSource as? Camera2Source ?: return
        scope.launch {
            streamMutex.withLock {
                withContext(Dispatchers.Default) { runCatching { camera.switchCamera() } }
            }
        }
    }

    /** Toggle the microphone mute for the broadcast (in Zundamon-voice mode: silence the voice, pause listening). */
    fun toggleMic() {
        if (_streamer.value?.audioSource is ZundamonAudioSource) {
            val muted = !_micMuted.value
            voiceSource.setMuted(muted)
            _micMuted.value = muted
            return
        }
        val mic = _streamer.value?.let { micSource(it) } ?: return
        if (mic.isMuted()) {
            mic.unMute()
            _micMuted.value = false
        } else {
            mic.mute()
            _micMuted.value = true
        }
    }

    fun toggleStream(streamKey: String) {
        val stream = _streamer.value ?: return
        val streamingOrTrying = _uiState.value.let {
            it is PublishUiState.Live || it is PublishUiState.Connecting || it is PublishUiState.Reconnecting
        }
        if (streamingOrTrying) {
            // User-requested stop: cancel any reconnect loop first so the drop isn't retried, and mark
            // `stopping` so the resulting onDisconnect doesn't surface as a spurious error.
            intendStreaming = false
            stopping = true
            reconnectJob?.cancel()
            runCatching { if (stream.isStreaming) stream.stopStream() }
            _uiState.value = PublishUiState.Idle
            stopping = false
        } else {
            if (streamKey.isBlank()) {
                _uiState.value = PublishUiState.Error(R.string.enter_stream_key)
                return
            }
            val url = TwitchIngest.rtmpUrl(streamKey)
            ingestUrl = url
            intendStreaming = true
            _uiState.value = PublishUiState.Connecting
            // startStream connects asynchronously; the outcome arrives via the ConnectChecker callbacks.
            runCatching { stream.startStream(url) }
                .onFailure {
                    intendStreaming = false
                    _uiState.value = PublishUiState.Error(R.string.connect_error)
                }
        }
    }

    // ---- ConnectChecker (RootEncoder callbacks; re-posted to the Main scope) --------------------
    //
    // Kept as a PRIVATE object (not `class PublishController : ConnectChecker`) so the RootEncoder type
    // doesn't leak into this controller's public API — otherwise every consumer module (e.g. :app)
    // would need RootEncoder on its classpath just to reference PublishController.
    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = Unit

        override fun onConnectionSuccess() {
            scope.launch {
                reconnectJob?.cancel()
                _uiState.value = PublishUiState.Live
            }
        }

        override fun onConnectionFailed(reason: String) {
            scope.launch {
                when {
                    intendStreaming -> startReconnect()
                    !stopping -> _uiState.value = PublishUiState.Error(R.string.connect_error)
                }
            }
        }

        override fun onDisconnect() {
            scope.launch {
                when {
                    intendStreaming -> startReconnect()
                    _uiState.value is PublishUiState.Live -> _uiState.value = PublishUiState.Idle
                }
            }
        }

        override fun onAuthError() {
            scope.launch {
                intendStreaming = false
                reconnectJob?.cancel()
                _uiState.value = PublishUiState.Error(R.string.key_rejected)
            }
        }

        override fun onAuthSuccess() = Unit
    }

    /** Retries [GenericStream.startStream] with exponential backoff after an unintended drop. */
    private fun startReconnect() {
        val url = ingestUrl ?: return
        val stream = _streamer.value ?: return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempt = 1
            while (intendStreaming) {
                val backoff = reconnectPolicy.delayForAttempt(attempt)
                if (backoff == null) {
                    intendStreaming = false
                    _uiState.value = PublishUiState.Error(R.string.lost_connection)
                    return@launch
                }
                _uiState.value = PublishUiState.Reconnecting(attempt)
                delay(backoff)
                if (!intendStreaming) return@launch
                // Close any half-open endpoint first so startStream actually re-handshakes.
                runCatching { if (stream.isStreaming) stream.stopStream() }
                val reconnected = runCatching { stream.startStream(url) }.isSuccess
                if (reconnected) return@launch // onConnectionSuccess confirms Live and cancels us.
                attempt++
            }
        }
    }

    // ---- Preview binding (fullscreen ⇄ mini share ONE always-running GL pipeline) ---------------
    //
    // The current preview target. The fullscreen and the mini each own a TextureView; on
    // minimize/maximize we SWAP the preview *surface* on the already-running GL render thread
    // (`GlStreamInterface.deAttachPreview()` + `attachPreview()`) — the Camera2 device and the GL
    // pipeline are never stopped, so the swap is flash-free and the incoming view gets live frames on
    // the next GL tick. Only the very first attach cold-starts the camera (`startPreview`). Callers
    // must invoke these from the TextureView's SurfaceTextureListener (surface guaranteed available),
    // never before the surface exists — otherwise the GL renders into nothing and the preview freezes.
    //
    // A leaving view only detaches if it is still the current target: this guards the swap race where
    // Compose composes the incoming view (which becomes `currentPreview`) before it destroys the old
    // one. The camera is torn down only by releaseCamera(), never by a plain detach.
    private var currentPreview: TextureView? = null

    // The Surface WE create to hand a swapped-in view to RootEncoder (the swap branch below).
    // RootEncoder's deAttachPreview()/attachPreview() release only the *EGLSurface*, never this Java
    // Surface, so we must release it ourselves or each minimize/maximize leaks a native BufferQueue
    // producer handle (reclaimed only at GC finalization). The cold-start branch hands RootEncoder the
    // view (it owns that Surface), so only swap-created Surfaces are tracked here. Main-thread only.
    private var previewSurface: Surface? = null

    /** Attach [view] as the live preview target; RootEncoder scales it via [aspectMode] (no stretch). */
    fun attachPreview(view: TextureView, aspectMode: AspectRatioMode = AspectRatioMode.Fill) {
        val stream = _streamer.value ?: return
        val surfaceTexture = view.surfaceTexture ?: return // caller ensures availability
        val ok = runCatching {
            val gl = stream.getGlInterface()
            if (stream.isStreaming || stream.isOnPreview) {
                // Pipeline already running (previewing another view, or live): swap the preview surface
                // only — no camera/GL restart, so no black flash.
                gl.deAttachPreview()
                previewSurface?.release() // its EGLSurface is gone (deAttach); free the backing Surface
                Surface(surfaceTexture).also {
                    previewSurface = it
                    gl.attachPreview(it)
                }
            } else {
                // Cold start: bring up the camera + GL render thread and attach this surface.
                // autoHandle = FALSE on purpose: the `true` path makes RootEncoder register its own
                // SurfaceTextureListener on this view, which auto-fires stopPreview() (closing the
                // camera + tearing down the GL thread) when the view is destroyed on minimize — that
                // froze the mini. We drive attach/detach ourselves via the view's listener instead;
                // orientation is handled independently by the GL interface, so this stays upright.
                //
                // KNOWN (benign): RootEncoder allocates its own preview Surface inside startPreview
                // (StreamBase). Under heavy navigation churn StrictMode's CloseGuard logs a
                // LeakedClosableViolation ("Surface.release not called") for it. Investigated: this
                // app pairs start/stopPreview correctly (releaseCamera → stopPreview → release) and
                // releases its own swap Surfaces; forcing stopPreview unconditionally at teardown does
                // NOT clear it, so the un-released Surface is RootEncoder-internal, not an app leak.
                // It is GC-reclaimed and LeakCanary-clean (no retained-object leak) — left as-is
                // rather than risk the flash-free swap design. See scripts/ai-dev-roadmap.md.
                stream.startPreview(view, false)
            }
            gl.setAspectRatioMode(aspectMode)
            // The Go Live screen is ALWAYS portrait content (portrait-locked) and the encoder is
            // portrait (see initialize), so the camera texture needs a CONSTANT orientation — NOT a
            // display-adaptive one. Using CameraHelper.getCameraOrientation(Display.getRotation()) made
            // it vary with the PHYSICAL rotation (90 when the device is physically portrait, 180 when
            // landscape), so entering from portrait vs landscape rotated the preview differently (one
            // upright, one 90° off). A fixed value keeps every entry consistent. Applied only while the
            // fullscreen shows (!minimized); the mini keeps this same orientation and cover-crops it.
            if (!_minimized.value) stream.setOrientation(FULLSCREEN_CAMERA_ROTATION)
            // Portrait content aspect for BOTH preview and stream (setIsPortrait = preview + stream):
            //  - preview Fill compares against the portrait aspect ⇒ round (not ~3.2x wide);
            //  - the 720x1280 portrait encoder full-fills the upright portrait content undistorted
            //    instead of stretch-filling a landscape 16:9 frame ⇒ fixes "9:16 streamed as stretched".
            // isPortrait only sets the VIEWPORT aspect; the upright rotation is setOrientation above.
            gl.setIsPortrait(true)
            if (view.width > 0 && view.height > 0) gl.setPreviewResolution(view.width, view.height)
        }.isSuccess
        // Only claim [view] as the current target once the attach actually succeeded, so a failed
        // attach doesn't leave updatePreviewResolution/detachPreview operating on a phantom target.
        if (ok) currentPreview = view
    }

    /** The preview's view size settled/changed — keep the GL render resolution in sync (no rebind). */
    fun updatePreviewResolution(view: TextureView, width: Int, height: Int) {
        if (currentPreview !== view || width <= 0 || height <= 0) return
        // Portrait was already asserted at attach (it persists), so only the resolution needs syncing.
        runCatching { _streamer.value?.getGlInterface()?.setPreviewResolution(width, height) }
    }

    /**
     * Detach [view]'s surface if it is still the current target (else it was already replaced by a
     * swap). Only removes the preview surface from the GL thread — the camera keeps running so the
     * incoming view (mini/fullscreen) attaches flash-free. Full teardown happens in [releaseCamera].
     */
    fun detachPreview(view: TextureView) {
        if (currentPreview !== view) return
        currentPreview = null
        runCatching { _streamer.value?.getGlInterface()?.deAttachPreview() }
        previewSurface?.release()
        previewSurface = null
    }

    fun minimize() = _minimized.update { true }

    fun maximize() = _minimized.update { false }

    /** Closes the floating camera and releases it (unless the fullscreen screen is still showing). */
    fun close() {
        _minimized.value = false
        if (!_screenActive.value) releaseCamera()
    }

    /** Stops any live broadcast and frees the camera — e.g. when the system PiP window is closed. */
    fun release() {
        _minimized.value = false
        _screenActive.value = false
        releaseCamera()
    }

    private fun releaseCamera() {
        val stream = _streamer.value ?: return
        intendStreaming = false
        stopping = false
        reconnectJob?.cancel()
        // Leaving the screen must never leave the voice's microphone open; the mode itself persists.
        voiceSource.stop()
        voiceSource.detachAudioSink()
        currentPreview = null
        previewSurface?.release()
        previewSurface = null
        _streamer.value = null
        _uiState.value = PublishUiState.Idle
        // Teardown off the main thread; tracked in teardownJob so a fast re-initialize() joins it
        // before opening the camera again (avoids two streams fighting over the Camera2 device).
        teardownJob = CoroutineScope(Dispatchers.Default).launch {
            // Behind the stream lock so an in-flight source swap / re-prepare completes first.
            streamMutex.withLock {
                runCatching { if (stream.isStreaming) stream.stopStream() }
                runCatching { if (stream.isOnPreview) stream.stopPreview() }
                runCatching { stream.release() }
            }
        }
    }

    private companion object {
        const val MAX_CHAT_MESSAGES = 100
        const val VIDEO_BITRATE = 4_000_000
        const val AUDIO_SAMPLE_RATE = 44_100
        const val AUDIO_BITRATE = 128_000

        // >1 makes the adaptive-bitrate reaction less aggressive (recover faster toward the max).
        const val BITRATE_ADAPT_FACTOR = 1f

        // Fixed camera-texture rotation for the portrait Go Live screen + portrait encoder. Constant
        // (not display-derived) so entering from any physical device orientation looks the same.
        const val FULLSCREEN_CAMERA_ROTATION = 0
    }
}
