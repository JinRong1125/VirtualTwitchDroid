package com.example.virtualtwitchdroid.feature.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.testing.data.TestData
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class BrowseScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Null thumbnails/avatars so Coil never performs a network fetch during the UI test.
    private val channels = TestData.sampleChannels.map { it.copy(thumbnailUrl = null, avatarUrl = null) }

    @Test
    fun success_showsTitleNameAndDescription() {
        composeTestRule.setContent {
            TwitchTheme {
                BrowseScreen(
                    uiState = BrowseUiState.Success(channels),
                    onChannelClick = { _, _ -> },
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Live Channels").assertExists()
        composeTestRule.onNodeWithText("Alice").assertExists()
        composeTestRule.onNodeWithText("Speedrunning all night").assertExists()
    }

    @Test
    fun clickingChannel_invokesCallbackWithLogin() {
        var clicked: String? = null
        composeTestRule.setContent {
            TwitchTheme {
                BrowseScreen(
                    uiState = BrowseUiState.Success(channels),
                    onChannelClick = { login, _ -> clicked = login },
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Alice").performClick()
        assertEquals("alice", clicked)
    }

    @Test
    fun error_showsMessageAndRetryTriggersCallback() {
        var retried = false
        composeTestRule.setContent {
            TwitchTheme {
                BrowseScreen(
                    uiState = BrowseUiState.Error(R.string.error_channels),
                    onChannelClick = { _, _ -> },
                    onRetry = { retried = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Couldn't load live channels.").assertExists()
        composeTestRule.onNodeWithText("Retry", substring = true).performClick()
        assertEquals(true, retried)
    }
}
