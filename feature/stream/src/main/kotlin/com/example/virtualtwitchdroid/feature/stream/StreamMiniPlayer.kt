package com.example.virtualtwitchdroid.feature.stream

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualtwitchdroid.core.designsystem.component.FloatingMiniWindow

/**
 * The floating in-app mini-player for a watched stream. It delegates the collapse/expand + drag
 * chrome to the shared [FloatingMiniWindow] and just supplies the video surface (a [TextureView],
 * so it draws over any camera preview and scales with the animation).
 */
@Composable
fun StreamMiniPlayer(
    controller: PlayerController,
    onExpand: (channelLogin: String, viewers: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by controller.session.collectAsStateWithLifecycle()
    val current = session ?: return
    FloatingMiniWindow(
        minimized = current.minimized,
        title = current.channelLogin,
        onExpand = { onExpand(current.channelLogin, current.viewers) },
        onClose = controller::close,
        modifier = modifier,
    ) {
        PlayerSurface(controller.exoPlayer, Modifier.fillMaxSize(), useTextureView = true)
    }
}
