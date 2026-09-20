package com.example.virtualtwitchdroid.feature.avatar.render

import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.virtualtwitchdroid.core.common.media.MouthTrackSource
import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

private const val TAG = "AvatarSurface"

/** Slightly under a 60 fps period so vsync jitter never skips a whole frame. */
private const val MIN_FRAME_INTERVAL_NANOS = 15_500_000L

/**
 * A Filament-rendered VRM avatar driven by [rig]. The [AvatarRenderer] is owned by the [TextureView]
 * it draws into: created in the `AndroidView` factory and released in its `onRelease`, so its lifetime
 * is exactly the view's — never a Compose state key's. The model at [assetPath] is read + parsed off the
 * main thread (gltfio's own upload is asynchronous) and frames are rendered via [Choreographer] while the
 * view is in the composition, capped at [MIN_FRAME_INTERVAL_NANOS] (≈60 fps) so a high-refresh panel does
 * not run the rig, springs and Filament at several times the tracker's rate.
 */
@Composable
fun AvatarSurface(
    rig: FaceRig,
    modifier: Modifier = Modifier,
    assetPath: String = BUNDLED_AVATAR_ASSET,
    mouthTrack: MouthTrackSource? = null,
) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<AvatarRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { view ->
                val created = AvatarRenderer().also { it.attachTo(view) }
                Log.i(TAG, "created renderer@${created.hashCode()}")
                view.tag = created
                renderer = created
            }
        },
        onRelease = { view ->
            (view.tag as? AvatarRenderer)?.let {
                Log.i(TAG, "releasing renderer@${it.hashCode()}")
                it.release()
                if (renderer === it) renderer = null
            }
        },
    )

    // Push the newest rig (and the voice's mouth track) to the renderer; applied on the next rendered frame.
    SideEffect {
        renderer?.rig = rig
        renderer?.mouthTrack = mouthTrack
    }

    LaunchedEffect(renderer, assetPath) {
        val r = renderer ?: return@LaunchedEffect
        Log.i(TAG, "renderer@${r.hashCode()}: loading $assetPath")
        val (glb, model) = withContext(Dispatchers.IO) { loadVrm(context, assetPath) }
        Log.i(
            TAG,
            "renderer@${r.hashCode()}: parsed ${model.title}, ${glb.capacity()} bytes, ${model.expressions.size} expressions",
        )
        r.load(glb, model)
    }

    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        Log.i(TAG, "renderer@${r.hashCode()}: render loop started")
        val choreographer = Choreographer.getInstance()
        var lastRenderNanos = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // A 120 Hz panel would otherwise run the rig, springs and Filament at 4× the tracker's rate.
                if (frameTimeNanos - lastRenderNanos >= MIN_FRAME_INTERVAL_NANOS) {
                    lastRenderNanos = frameTimeNanos
                    r.render(frameTimeNanos)
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        try {
            awaitCancellation()
        } finally {
            choreographer.removeFrameCallback(callback)
        }
    }
}
