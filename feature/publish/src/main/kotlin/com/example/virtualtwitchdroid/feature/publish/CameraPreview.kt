package com.example.virtualtwitchdroid.feature.publish

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.generic.GenericStream

/**
 * Renders the RootEncoder camera preview into a [TextureView], attached to the streamer's single,
 * always-running GL pipeline via [PublishController.attachPreview]. Scale is RootEncoder's
 * `AspectRatioMode` cover-fill combined with the portrait flags the controller sets
 * (`setIsPortrait(true)` for preview+stream, plus a display-adaptive `setOrientation` for the upright
 * rotation), so there is **no hand-rolled transform** — the same component serves the fullscreen and
 * the floating mini, and swapping between them only swaps the GL preview *surface* (no camera
 * reconfigure, no black flash).
 *
 * Attach/detach is driven by the [TextureView]'s [TextureView.SurfaceTextureListener], NOT by the
 * composition lifecycle: RootEncoder needs a live [SurfaceTexture], and the mini's is not ready at the
 * instant it enters composition. Attaching before it exists renders into nothing and freezes the
 * preview — so we wait for [onSurfaceTextureAvailable] and release on [onSurfaceTextureDestroyed].
 *
 * A [TextureView] (not RootEncoder's SurfaceView-backed `OpenGlView`) is used so the mini composites
 * cleanly over the app with no SurfaceView hole-punch.
 */
@Composable
internal fun CameraPreview(
    controller: PublishController,
    streamer: GenericStream,
    modifier: Modifier = Modifier,
    aspectMode: AspectRatioMode = AspectRatioMode.Fill,
) {
    // Hold the view so the DisposableEffect can force a detach if the composable leaves while the
    // surface is still alive (e.g. a fast navigation that doesn't destroy the TextureView first).
    val viewRef = remember { arrayOfNulls<TextureView>(1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { view ->
                viewRef[0] = view
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        controller.attachPreview(view, aspectMode)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        // The view resized (mini collapse animation / rotation) — keep the GL render
                        // resolution in sync without re-binding the camera.
                        controller.updatePreviewResolution(view, w, h)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        controller.detachPreview(view)
                        return true // safe to release; the GL thread no longer targets this surface
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )

    DisposableEffect(streamer, aspectMode) {
        // If the surface is already available when (re)composing with a new streamer/aspect, attach
        // now — the listener only fires once per surface lifetime. onDispose covers the case where the
        // composable leaves before the surface is destroyed.
        viewRef[0]?.let { if (it.isAvailable) controller.attachPreview(it, aspectMode) }
        onDispose { viewRef[0]?.let { controller.detachPreview(it) } }
    }
}
