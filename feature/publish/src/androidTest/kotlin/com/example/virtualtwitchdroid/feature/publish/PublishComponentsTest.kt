package com.example.virtualtwitchdroid.feature.publish

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose tests for the publish screen's stateless pieces — the "Go Live" analogue of
 * the stream screen's state tests. Each is driven directly with mock inputs (no live broadcast, no
 * camera), asserting the visible state and the callbacks via semantics.
 */
class PublishComponentsTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun broadcastButton_idle_showsGoLive_isEnabled_andClicks() {
        var clicks = 0
        rule.setContent {
            TwitchTheme {
                BroadcastButton(isLive = false, isConnecting = false, isReconnecting = false, onClick = { clicks++ })
            }
        }
        rule.onNodeWithText("Go Live").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun broadcastButton_connecting_showsConnecting_andIsDisabled() {
        var clicks = 0
        rule.setContent {
            TwitchTheme {
                BroadcastButton(isLive = false, isConnecting = true, isReconnecting = false, onClick = { clicks++ })
            }
        }
        rule.onNodeWithText("Connecting…").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, clicks) } // disabled → no callback
    }

    @Test
    fun broadcastButton_reconnecting_showsReconnecting_andStaysEnabledToCancel() {
        rule.setContent {
            TwitchTheme {
                BroadcastButton(isLive = false, isConnecting = false, isReconnecting = true, onClick = {})
            }
        }
        rule.onNodeWithText("Reconnecting…").assertIsEnabled()
    }

    @Test
    fun broadcastButton_live_showsGoBackstage() {
        rule.setContent {
            TwitchTheme {
                BroadcastButton(isLive = true, isConnecting = false, isReconnecting = false, onClick = {})
            }
        }
        rule.onNodeWithText("Go Backstage").assertIsEnabled()
    }

    @Test
    fun cameraErrorState_showsMessage_andRetryInvokesCallback() {
        var retries = 0
        rule.setContent {
            TwitchTheme { CameraErrorState(message = "Couldn't open the camera.", onRetry = { retries++ }) }
        }
        rule.onNodeWithText("Couldn't open the camera.").assertIsDisplayed()
        rule.onNodeWithText("Retry", substring = true).performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun permissionPrompt_showsRationale_andGrantInvokesCallback() {
        var grants = 0
        rule.setContent {
            TwitchTheme { PermissionPrompt(onGrant = { grants++ }) }
        }
        rule.onNodeWithText("Camera and microphone", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Grant access").performClick()
        rule.runOnIdle { assertEquals(1, grants) }
    }
}
