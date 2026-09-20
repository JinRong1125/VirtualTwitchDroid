package com.example.virtualtwitchdroid.feature.avatar.tracking

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlinx.coroutines.awaitCancellation

/**
 * Binds a camera's `ImageAnalysis` use case (the **front** lens by default, the back lens when
 * [frontCamera] is false) to the composition's lifecycle and feeds every frame into [tracker] — see
 * [bindFaceTracking] for the camera setup. Leaving the composition (or switching lens) unbinds exactly
 * what this effect bound.
 */
@Composable
fun CameraTrackingEffect(tracker: FaceTracker, frontCamera: Boolean = true) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, tracker, frontCamera) {
        bindFaceTracking(context, lifecycleOwner, tracker, frontCamera)
    }
}

/**
 * Feeds camera frames into [tracker] until cancelled: binds an `ImageAnalysis` use case on the front or
 * back lens to [lifecycleOwner] and hands each frame over with its sensor rotation — the tracker rotates,
 * and nothing is mirrored (the rig mapper applies the selfie mirror in rig space, front lens only, so
 * ARKit "Left/Right" keep their subject-relative meaning). No preview: the camera is for tracking only;
 * viewers see the avatar.
 *
 * Budget choices (see `scripts/vtuber-avatar-plan.md` §6): ~640×480 analysis frames (the landmarker
 * doesn't need more), `KEEP_ONLY_LATEST` so a slow inference drops frames instead of queueing lag, and
 * a dedicated single-thread executor off the main thread. Cancellation unbinds the use case and stops
 * the executor. Must be called on the main thread (CameraX binding). [onBound] receives the bound
 * [Camera] (e.g. to watch its close state at teardown).
 */
suspend fun bindFaceTracking(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    tracker: FaceTracker,
    frontCamera: Boolean,
    onBound: (Camera) -> Unit = {},
): Nothing {
    val executor = Executors.newSingleThreadExecutor()
    val provider = ProcessCameraProvider.awaitInstance(context)
    val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
    // toBitmap() validates the RGBA stride (a mismatch throws instead of silently skewing the image).
    analysis.setAnalyzer(executor) { image ->
        image.use { tracker.process(it.toBitmap(), it.imageInfo.rotationDegrees, SystemClock.uptimeMillis()) }
    }
    val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    try {
        onBound(provider.bindToLifecycle(lifecycleOwner, selector, analysis))
        awaitCancellation()
    } finally {
        analysis.clearAnalyzer()
        provider.unbind(analysis)
        executor.shutdown()
    }
}

private const val ANALYSIS_WIDTH = 640
private const val ANALYSIS_HEIGHT = 480
