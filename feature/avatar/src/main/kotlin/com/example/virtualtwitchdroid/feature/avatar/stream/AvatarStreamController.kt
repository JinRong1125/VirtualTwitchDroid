package com.example.virtualtwitchdroid.feature.avatar.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.example.virtualtwitchdroid.core.common.media.AvatarStreamSource
import com.example.virtualtwitchdroid.core.common.media.MouthTrackSource
import com.example.virtualtwitchdroid.feature.avatar.AvatarSupport
import com.example.virtualtwitchdroid.feature.avatar.render.AvatarRenderer
import com.example.virtualtwitchdroid.feature.avatar.render.BUNDLED_AVATAR_ASSET
import com.example.virtualtwitchdroid.feature.avatar.render.loadVrm
import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRigPipeline
import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTracker
import com.example.virtualtwitchdroid.feature.avatar.tracking.MediaPipeFaceTracker
import com.example.virtualtwitchdroid.feature.avatar.tracking.bindFaceTracking
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app-scoped [AvatarStreamSource]: while a publisher has an output attached, one **session** runs —
 * a headless [AvatarRenderer] drawing into the publisher's surface at ≤ [TARGET_FPS], a fresh face
 * tracker fed by the chosen camera lens, and the shared [FaceRigPipeline] between them. Detaching tears
 * the whole session down (camera, model, engine, surface).
 *
 * Everything session-related runs on the **main thread** (Filament, CameraX binding and the
 * `LifecycleRegistry` all require one thread; the on-screen avatar already renders there). Attach/detach
 * arrive from RootEncoder's worker threads and are marshalled over, ordered by a generation counter so a
 * detach always cancels a still-queued attach. Detach **waits** for the teardown — and then for CameraX
 * to report the camera CLOSED — because the caller reconnects its own camera to the same buffer queue
 * (and possibly the same lens) right after.
 *
 * The camera is bound to a private [LifecycleOwner] that is RESUMED for the session's life and DESTROYED
 * at teardown; CameraX's close is asynchronous, hence the explicit wait above.
 */
@Singleton
class AvatarStreamController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val support: AvatarSupport,
    private val trackers: Provider<MediaPipeFaceTracker>,
    private val mouthTrack: MouthTrackSource,
) : AvatarStreamSource {

    override val supported: Boolean get() = support.supported

    private val _frontCamera = MutableStateFlow(true)
    override val frontCamera: StateFlow<Boolean> = _frontCamera.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: Session? = null // main thread only

    /** Bumped by every attach and detach; a queued attach that is no longer current is dropped. */
    private val generation = AtomicInteger()

    override fun toggleCamera() = _frontCamera.update { !it }

    override fun attachOutput(surface: Surface, width: Int, height: Int) {
        val ticket = generation.incrementAndGet()
        onMain {
            if (generation.get() != ticket) {
                Log.w(TAG, "attach superseded before it ran — releasing the surface")
                surface.release()
                return@onMain
            }
            session?.close()
            session = runCatching { Session(surface, width, height) }
                .onFailure { Log.e(TAG, "avatar session failed to start", it) }
                .getOrNull()
            if (session != null) Log.i(TAG, "session started (${width}x$height)")
        }
    }

    override fun detachOutput() {
        generation.incrementAndGet()
        val cameraClosed = CountDownLatch(1)
        // Blocking window kept to what RootEncoder needs before it reclaims the surface: the swap chain is
        // destroyed and flushed (tens of ms). The rest (camera, tracker, scope) is queued on main from INSIDE
        // this block, so even if the caller's wait below times out the teardown still completes — never a leak.
        val released = onMainBlocking {
            val s = session ?: run {
                cameraClosed.countDown()
                return@onMainBlocking
            }
            session = null
            s.releaseOutput()
            mainHandler.post {
                s.closeRest(cameraClosed)
                Log.i(TAG, "session closed")
            }
        }
        if (!released) Log.e(TAG, "avatar output was NOT released in time — the surface may still be owned")
        // Off the main thread only (main can't block): let CameraX finish closing the camera before the
        // publisher's own camera source reopens it — on the same lens that would fail with CAMERA_IN_USE.
        if (Looper.myLooper() == Looper.getMainLooper()) return
        if (!cameraClosed.await(CAMERA_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "camera did not report CLOSED in time")
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Runs [block] on main and waits for it; false when main did not get to it within [DETACH_TIMEOUT_MS]. */
    private fun onMainBlocking(block: () -> Unit): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return true
        }
        val done = CountDownLatch(1)
        mainHandler.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        // Bounded: a wedged main thread must not deadlock RootEncoder's worker for good.
        val ok = done.await(DETACH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!ok) Log.e(TAG, "detachOutput timed out waiting for main — the avatar may still hold the surface")
        return ok
    }

    /** One attached output: renderer + tracker + camera + the rig pipeline between them. Main thread. */
    private inner class Session(surface: Surface, width: Int, height: Int) : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        // A failure inside the session (e.g. the requested lens does not exist on this device) must not
        // take the process down; the avatar then simply renders its neutral pose.
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, e -> Log.e(TAG, "avatar session task failed", e) },
        )

        // The tracker first: if the provider throws there is no Filament Engine (a native driver thread) to leak.
        private val tracker: FaceTracker = trackers.get()
        private val renderer = AvatarRenderer()
        private val pipeline = FaceRigPipeline(clock = SystemClock::uptimeMillis)
        private val choreographer = Choreographer.getInstance()
        private var lastRenderNanos = 0L
        private var camera: Camera? = null

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // The encoder runs at TARGET_FPS; rendering every display frame would only burn GPU/CPU.
                if (frameTimeNanos - lastRenderNanos >= MIN_FRAME_INTERVAL_NANOS) {
                    lastRenderNanos = frameTimeNanos
                    renderer.render(frameTimeNanos)
                }
                choreographer.postFrameCallback(this)
            }
        }

        init {
            registry.currentState = Lifecycle.State.RESUMED
            renderer.mouthTrack = mouthTrack // the Zundamon voice owns the mouth while it is active
            renderer.mouthLookaheadNanos = NANOS_PER_SECOND / TARGET_FPS // lips land on the frame that is encoded
            try {
                renderer.attachSurface(surface, width, height)
            } catch (e: RuntimeException) {
                renderer.release() // never leak the Engine when the publisher's surface is already gone
                tracker.close()
                throw e
            }
            scope.launch {
                val (glb, model) = withContext(Dispatchers.IO) { loadVrm(context, BUNDLED_AVATAR_ASSET) }
                renderer.load(glb, model)
            }
            // Mapper + smoother + baseline run off main; `renderer.rig` is volatile and read on the next frame.
            scope.launch(Dispatchers.Default) { pipeline.rigs(tracker.frames).collect { renderer.rig = it } }
            scope.launch {
                frontCamera.collectLatest { front ->
                    pipeline.setLens(front)
                    bindFaceTracking(context, this@Session, tracker, front) { camera = it }
                }
            }
            choreographer.postFrameCallback(frameCallback)
        }

        /** Step 1 of teardown (blocking for RootEncoder): stop rendering, destroy the flushed swap chain, release the surface. */
        fun releaseOutput() {
            choreographer.removeFrameCallback(frameCallback)
            renderer.release()
        }

        /** Step 2 (unblocked, main): camera, tracker and tasks; [cameraClosed] opens once CameraX reports CLOSED. */
        fun closeRest(cameraClosed: CountDownLatch) {
            val state = camera?.cameraInfo?.cameraState
            if (state == null) {
                cameraClosed.countDown()
            } else {
                state.observeForever(object : Observer<CameraState> {
                    override fun onChanged(value: CameraState) {
                        if (value.type == CameraState.Type.CLOSED) {
                            state.removeObserver(this)
                            cameraClosed.countDown()
                        }
                    }
                })
            }
            try {
                registry.currentState = Lifecycle.State.DESTROYED // CameraX starts closing the camera
                scope.cancel()
            } finally {
                tracker.close()
            }
        }

        /** Full teardown in one go (used when a newer attach supersedes this session on main). */
        fun close(): CountDownLatch {
            releaseOutput()
            return CountDownLatch(1).also { closeRest(it) }
        }
    }

    private companion object {
        const val TAG = "AvatarStreamController"
        const val TARGET_FPS = 30
        const val NANOS_PER_SECOND = 1_000_000_000L

        /** Slightly under a 30 fps period so vsync jitter never skips a whole frame. */
        const val MIN_FRAME_INTERVAL_NANOS = NANOS_PER_SECOND / TARGET_FPS - 1_500_000L
        const val DETACH_TIMEOUT_MS = 2_000L
        const val CAMERA_CLOSE_TIMEOUT_MS = 1_500L
    }
}
