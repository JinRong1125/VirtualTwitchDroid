package com.example.virtualtwitchdroid.feature.stream

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * A dumb video surface bound to an externally-owned [exoPlayer] (see [PlayerController]).
 *
 * It never builds or releases a player of its own, so the *same* [exoPlayer] can be attached to a
 * fresh surface in each place it appears — the fullscreen watch screen, the landscape/PiP layouts,
 * and the floating in-app mini-player. Re-attaching an already-playing [ExoPlayer] to a new
 * surface hands over the video output without re-buffering, so playback stays continuous as the
 * surface moves between screens.
 *
 * @param useTextureView renders into a [TextureView] instead of a [PlayerView]'s `SurfaceView`.
 *   A `SurfaceView` is a separate composited layer that (a) is drawn over by other camera/video
 *   `SurfaceView`s and (b) does not scale with `graphicsLayer`. The floating mini-player therefore
 *   uses a `TextureView`, which composites *in-window* — so it draws on top of the Publish camera
 *   preview and scales cleanly during the collapse/expand animation.
 */
@Composable
internal fun PlayerSurface(exoPlayer: ExoPlayer, modifier: Modifier = Modifier, useTextureView: Boolean = false) {
    if (useTextureView) {
        AndroidView(
            modifier = modifier,
            factory = { ctx -> TextureView(ctx).also { exoPlayer.setVideoTextureView(it) } },
            // Reclaim the video output when this surface re-enters (e.g. after the fullscreen
            // PlayerView took it over, then the user minimized again).
            update = { view -> exoPlayer.setVideoTextureView(view) },
        )
    } else {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // clean surface — a custom overlay provides the chrome
                }
            },
            update = { view -> if (view.player !== exoPlayer) view.player = exoPlayer },
        )
    }
}
