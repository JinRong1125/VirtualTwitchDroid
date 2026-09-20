package com.example.virtualtwitchdroid.feature.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualtwitchdroid.core.designsystem.component.FloatingMiniWindow
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.designsystem.theme.XtraLiveRed
import com.example.virtualtwitchdroid.core.designsystem.theme.pillCentered

/**
 * The floating in-app mini-player for the publish camera — always the real, real-time self-view, both
 * off-air and while live. It renders the SAME [CameraPreview] as the fullscreen screen; the shared
 * [PublishController] attaches the camera's single always-running RootEncoder GL preview to whichever
 * view (fullscreen or mini) is currently showing, so minimizing/expanding swaps the preview target
 * with no camera reconfigure (no black flash) and RootEncoder scales the mini correctly (no stretch).
 */
@Composable
fun PublishMiniPlayer(controller: PublishController, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val minimized by controller.minimized.collectAsStateWithLifecycle()
    val streamer by controller.streamer.collectAsStateWithLifecycle()
    val uiState by controller.uiState.collectAsStateWithLifecycle()

    val currentStreamer = streamer ?: return
    val isLive = uiState is PublishUiState.Live

    FloatingMiniWindow(
        minimized = minimized,
        title = if (isLive) stringResource(R.string.youre_live) else stringResource(R.string.camera),
        onExpand = onExpand,
        onClose = controller::close,
        modifier = modifier,
        // Camera's own PORTRAIT aspect (9:16, the 720×1280 encode) so the full upright camera shows
        // undistorted. Portrait: docked at ⅓ the screen WIDTH. Landscape: a tall 9:16 window can't fit
        // a width-based dock on the short side, so size it to ½ the screen HEIGHT there.
        videoAspectRatio = cameraViewAspectRatio(),
        dockWidthFraction = 1f / 3f,
        landscapeHeightFraction = 1f / 2f,
        badge = if (isLive) {
            {
                Text(
                    text = stringResource(R.string.live),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.pillCentered(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(XtraLiveRed, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                )
            }
        } else {
            null
        },
    ) {
        CameraPreview(controller = controller, streamer = currentStreamer, modifier = Modifier.fillMaxSize())
    }
}
