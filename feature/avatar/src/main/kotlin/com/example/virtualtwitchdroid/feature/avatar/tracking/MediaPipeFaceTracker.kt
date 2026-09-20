package com.example.virtualtwitchdroid.feature.avatar.tracking

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [FaceTracker] backed by MediaPipe Face Landmarker in LIVE_STREAM mode, with the ARKit blendshapes
 * and the facial transformation matrix enabled — the two outputs the rig mapper consumes. The model is
 * bundled in this module's assets ([MODEL_ASSET]).
 *
 * Delegate policy: try the GPU delegate first, then CPU. Initialization is attempted **once** per
 * delegate (a failed init latches — no per-frame retry storm), and a GPU failure that only shows up at
 * inference time (MediaPipe's error listener, common on some OEM GL stacks) triggers a one-time rebuild
 * on CPU. Either way the outcome is published on [status] so the UI never mistakes "broken" for "no face".
 *
 * Threading: [process] runs on the camera's single analysis thread and owns `landmarker`; MediaPipe
 * delivers results and errors serially on its own thread, which only writes the state flows and the
 * volatile fallback flag; [close] may come from the main thread.
 */
class MediaPipeFaceTracker @Inject constructor(@ApplicationContext private val context: Context) : FaceTracker {

    private val _frames = MutableStateFlow(FaceTrackingFrame.noFace(timestampMs = 0L))
    override val frames: StateFlow<FaceTrackingFrame> = _frames.asStateFlow()

    private val _stats = MutableStateFlow(TrackerStats.EMPTY)
    override val stats: StateFlow<TrackerStats> = _stats.asStateFlow()

    private val _status = MutableStateFlow<TrackerStatus>(TrackerStatus.Starting)
    override val status: StateFlow<TrackerStatus> = _status.asStateFlow()

    private val statsCalculator = TrackerStatsCalculator() // result-thread only

    // Analyzer-thread state.
    private var landmarker: FaceLandmarker? = null
    private var delegate: Delegate = Delegate.GPU
    private var initAttempted = false
    private var lastTimestampMs = Long.MIN_VALUE

    @Volatile private var closed = false

    /** Set by the MediaPipe error listener when the GPU delegate fails at inference time. */
    @Volatile private var cpuFallbackRequested = false

    /**
     * Rotation handed to MediaPipe per submitted frame (keyed by its timestamp), so the async result can
     * un-rotate its head matrix — MediaPipe reports it in the un-rotated sensor frame (see
     * [RotationMath.unrotateAboutZ]). Written on the analyzer thread, consumed on the result thread.
     */
    private val submittedRotations = ConcurrentHashMap<Long, Int>()

    /** Serialises [process] (analyzer thread) against [close] (main): closing a landmarker mid-detect is a native crash. */
    private val lock = Any()

    override fun process(frame: Bitmap, rotationDegrees: Int, timestampMs: Long) {
        synchronized(lock) { processLocked(frame, rotationDegrees, timestampMs) }
    }

    private fun processLocked(frame: Bitmap, rotationDegrees: Int, timestampMs: Long) {
        if (closed) return
        if (cpuFallbackRequested) {
            cpuFallbackRequested = false
            landmarker?.close()
            landmarker = null
            delegate = Delegate.CPU
            initAttempted = false
        }
        val fl = landmarker ?: createOnce() ?: return
        // MediaPipe requires strictly increasing timestamps; drop out-of-order frames.
        if (timestampMs <= lastTimestampMs) return
        lastTimestampMs = timestampMs
        val processing = ImageProcessingOptions.builder().setRotationDegrees(rotationDegrees).build()
        submittedRotations[timestampMs] = rotationDegrees
        runCatching { fl.detectAsync(BitmapImageBuilder(frame).build(), processing, timestampMs) }
            .onFailure {
                submittedRotations.remove(timestampMs)
                Log.w(TAG, "detectAsync failed", it)
            }
    }

    override fun close() = synchronized(lock) {
        closed = true
        landmarker?.close()
        landmarker = null
    }

    /** One initialization attempt per delegate chain; a failure latches until a fallback is requested. */
    private fun createOnce(): FaceLandmarker? {
        if (initAttempted) return null
        initAttempted = true
        val candidates = if (delegate == Delegate.CPU) listOf(Delegate.CPU) else listOf(Delegate.GPU, Delegate.CPU)
        for (candidate in candidates) {
            val created = runCatching { FaceLandmarker.createFromOptions(context, options(candidate)) }
            created.onSuccess {
                delegate = candidate
                landmarker = it
                _status.value = TrackerStatus.Running(candidate.name)
                return it
            }
            Log.w(TAG, "FaceLandmarker init failed on $candidate", created.exceptionOrNull())
        }
        _status.value = TrackerStatus.Failed(TrackerStatus.FailureReason.INIT_FAILED)
        return null
    }

    private fun options(delegate: Delegate): FaceLandmarker.FaceLandmarkerOptions =
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).setDelegate(delegate).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { onError(it) }
            .build()

    private fun onResult(result: FaceLandmarkerResult) {
        val now = SystemClock.uptimeMillis()
        _stats.value = statsCalculator.record(captureTimestampMs = result.timestampMs(), resultTimestampMs = now)
        val blendshapes = result.faceBlendshapes().orElse(null)?.firstOrNull()?.associate {
            it.categoryName() to
                it.score()
        }
        val matrix = result.facialTransformationMatrixes().orElse(null)?.firstOrNull()
        val rotation = submittedRotations.remove(result.timestampMs()) ?: 0
        // Dropped frames (KEEP_ONLY_LATEST) never get a result; don't let their entries accumulate.
        if (submittedRotations.size >
            MAX_PENDING_ROTATIONS
        ) {
            submittedRotations.keys.removeIf { it < result.timestampMs() }
        }
        _frames.value = toFrame(blendshapes, matrix, result.timestampMs(), rotation)
    }

    private fun onError(error: RuntimeException) {
        Log.w(TAG, "FaceLandmarker error on $delegate", error)
        if (delegate == Delegate.GPU && !cpuFallbackRequested) {
            cpuFallbackRequested = true // the analyzer thread rebuilds on CPU with the next frame
        } else {
            _status.value = TrackerStatus.Failed(TrackerStatus.FailureReason.RUNTIME_ERROR)
        }
    }

    internal companion object {
        const val TAG = "MediaPipeFaceTracker"
        const val MODEL_ASSET = "face_landmarker.task"
        const val MAX_PENDING_ROTATIONS = 64

        /**
         * A result's payload as a [FaceTrackingFrame]: no blendshapes means no face was found; the head
         * matrix is optional (the rig then keeps the head straight) and is un-rotated by the image
         * [rotationDegrees] the frame was submitted with. Pure, so it is unit-tested.
         */
        fun toFrame(
            blendshapes: Map<String, Float>?,
            headMatrix: FloatArray?,
            timestampMs: Long,
            rotationDegrees: Int = 0,
        ): FaceTrackingFrame = if (blendshapes == null) {
            FaceTrackingFrame.noFace(timestampMs)
        } else {
            val upright = headMatrix?.let { RotationMath.unrotateAboutZ(it, rotationDegrees.toFloat()) }
            FaceTrackingFrame(blendshapes, upright, timestampMs, faceDetected = true)
        }
    }
}
