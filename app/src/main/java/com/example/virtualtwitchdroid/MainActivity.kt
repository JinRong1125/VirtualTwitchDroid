package com.example.virtualtwitchdroid

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.feature.avatar.AvatarSupport
import com.example.virtualtwitchdroid.feature.publish.PublishController
import com.example.virtualtwitchdroid.feature.stream.PlayerController
import com.example.virtualtwitchdroid.ui.TwitchApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** App-scoped player, shared between the fullscreen stream screen and the floating mini-player. */
    @Inject
    lateinit var playerController: PlayerController

    /** App-scoped camera, shared between the fullscreen publish screen and its floating mini-player. */
    @Inject
    lateinit var publishController: PublishController

    /** Device gate for the VRM avatar (hides the Avatar tab where it can't render). */
    @Inject
    lateinit var avatarSupport: AvatarSupport

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The bottom NavigationBar draws its own color to the screen edge; stop the system adding a
        // translucent scrim behind it (edge-to-edge best practice, API 29+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            TwitchTheme {
                TwitchApp(playerController, publishController, avatarSupported = avatarSupport.supported)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Dismissing the system PiP window (dragging it to the bottom close target) stops the
        // activity while it is STILL in PiP mode — whereas expanding back to full screen does not
        // call onStop. So this reliably detects "closed the PiP" and stops the live broadcast
        // (same effect as Go Backstage) and frees the camera.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            publishController.release()
        }
    }

    override fun onDestroy() {
        // Free the media stack only when leaving for good (not on config-change recreation).
        // Closing the system PiP window finishes the activity, so this also stops a live broadcast
        // and releases the camera when the user dismisses the PiP.
        if (isFinishing) {
            playerController.release()
            publishController.release()
        }
        super.onDestroy()
    }
}
