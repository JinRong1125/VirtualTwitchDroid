package com.example.virtualtwitchdroid.feature.publish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.virtualtwitchdroid.core.designsystem.theme.Sizing
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Layout contract of the Go Live camera pane's overlay controls: avatar toggle top-right with the avatar
 * voice toggle under it; mic on top of settings bottom-left; Go Live centred at the bottom; PiP
 * bottom-right. Asserted on real measured bounds (no live broadcast, no camera). Each control's node is
 * its clickable [Sizing.lg] circle, padded by [Spacing.md] — hence the offsets below.
 */
class PublishOverlayControlsTest {

    @get:Rule val rule = createComposeRule()

    /** The broadcast slot height reported through `onBroadcastSize`, converted to dp by the test's density. */
    private var reportedBroadcastHeight: Dp = 0.dp

    private fun setAll(settingsVisible: Boolean = true, vtuberMode: Boolean = true, onToggleMic: () -> Unit = {}) =
        rule.setContent {
            TwitchTheme {
                val density = LocalDensity.current
                Box(Modifier.fillMaxSize()) {
                    PublishOverlayControls(
                        onBroadcastSize = { reportedBroadcastHeight = with(density) { it.toDp() } },
                        isLive = false,
                        isConnecting = false,
                        isReconnecting = false,
                        onToggleStream = {},
                        settingsVisible = settingsVisible,
                        onSettings = {},
                        micMuted = false,
                        onToggleMic = onToggleMic,
                        avatarSupported = true,
                        vtuberMode = vtuberMode,
                        onToggleVtuber = {},
                        voiceSupported = true,
                        zundamonVoice = false,
                        onToggleVoice = {},
                        onPip = {},
                    )
                }
            }
        }

    private fun bounds(contentDescription: String): DpRect =
        rule.onNodeWithContentDescription(contentDescription).getUnclippedBoundsInRoot()

    private fun root(): DpRect = rule.onRoot().getUnclippedBoundsInRoot()

    private fun DpRect.centerX(): Dp = (left + right) / 2f

    private fun assertClose(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) <= 1f, "$what: expected $expected, was $actual")

    /** From a circle's edge to the padded button's outer edge. */
    private val buttonInset = Spacing.md

    /** Gap between two adjacent circles. */
    private val gap = buttonInset * 2f

    /** Material 3's `Button` is laid out inside a 48 dp minimum touch target, 4 dp beyond its 40 dp face. */
    private val touchTargetSlack = (48.dp - Sizing.lg) / 2f

    @Test
    fun avatarToggle_isTopRight_withTheVoiceToggleDirectlyUnderIt() {
        setAll()
        val root = root()
        val avatar = bounds("Broadcast the camera") // VTuber mode is on → the toggle offers the camera
        val voice = bounds("Speak with the Zundamon voice")
        assertClose(root.right - buttonInset, avatar.right, "avatar hugs the right edge")
        assertClose(root.top + buttonInset, avatar.top, "avatar hugs the top edge")
        assertClose(avatar.bottom + gap, voice.top, "voice sits directly under the avatar toggle")
        assertClose(avatar.centerX(), voice.centerX(), "voice is in the same column as the avatar toggle")
    }

    @Test
    fun micSitsOnTopOfSettings_atTheBottomLeft() {
        setAll()
        val root = root()
        val mic = bounds("Mute microphone")
        val settings = bounds("Video quality")
        assertClose(root.left + buttonInset, settings.left, "settings hugs the left edge")
        assertClose(root.bottom - buttonInset, settings.bottom, "settings hugs the bottom edge")
        assertClose(settings.top - gap, mic.bottom, "mic sits directly on top of settings")
        assertClose(settings.centerX(), mic.centerX(), "mic is in the same column as settings")
    }

    @Test
    fun goLive_isCentredAtTheBottom_andPipIsBottomRight() {
        setAll()
        val root = root()
        val goLive = rule.onNodeWithText("Go Live").getUnclippedBoundsInRoot()
        assertClose(root.centerX(), goLive.centerX(), "Go Live is horizontally centred")
        assertClose(root.bottom - Spacing.md - touchTargetSlack, goLive.bottom, "Go Live sits at the bottom")
        // What the chat overlay pads for: the 48 dp touch target plus the bottom margin.
        rule.runOnIdle { assertClose(48.dp + Spacing.md, reportedBroadcastHeight, "reported broadcast slot height") }
        val pip = bounds("Picture in picture")
        assertClose(root.right - buttonInset, pip.right, "PiP hugs the right edge")
        assertClose(root.bottom - buttonInset, pip.bottom, "PiP hugs the bottom edge")
    }

    @Test
    fun hiddenControls_dropOut_andTheStacksCloseUp() {
        var micTaps = 0
        setAll(settingsVisible = false, vtuberMode = false, onToggleMic = { micTaps++ })
        rule.onNodeWithContentDescription("Video quality").assertDoesNotExist()
        rule.onNodeWithContentDescription("Speak with the Zundamon voice").assertDoesNotExist()
        val root = root()
        assertClose(root.bottom - buttonInset, bounds("Mute microphone").bottom, "mic drops to the bottom edge")
        assertClose(
            root.top + buttonInset,
            bounds("Broadcast the avatar (VTuber mode)").top,
            "avatar toggle stays top-right when the voice toggle is hidden",
        )
        rule.onNodeWithContentDescription("Mute microphone").performClick()
        rule.runOnIdle { assertEquals(1, micTaps) }
    }
}
