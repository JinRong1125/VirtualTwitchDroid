package com.example.virtualtwitchdroid.feature.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.testing.data.TestData
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class CategoryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Null thumbnails/avatars so Coil never performs a network fetch during the UI test.
    private val channels = TestData.sampleChannels.map { it.copy(thumbnailUrl = null, avatarUrl = null) }

    @Test
    fun showsTitleAndChannels() {
        composeTestRule.setContent {
            TwitchTheme {
                CategoryScreen(
                    title = "Just Chatting",
                    uiState = BrowseUiState.Success(channels),
                    onChannelClick = { _, _ -> },
                    onBackClick = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Just Chatting").assertExists()
        composeTestRule.onNodeWithText("Alice").assertExists()
    }

    @Test
    fun backButton_isPresentAndClickable() {
        var backed = false
        composeTestRule.setContent {
            TwitchTheme {
                CategoryScreen(
                    title = "Chess",
                    uiState = BrowseUiState.Success(channels),
                    onChannelClick = { _, _ -> },
                    onBackClick = { backed = true },
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        assertEquals(true, backed)
    }

    @Test
    fun clickingChannel_invokesCallbackWithLogin() {
        var clicked: String? = null
        composeTestRule.setContent {
            TwitchTheme {
                CategoryScreen(
                    title = "Chess",
                    uiState = BrowseUiState.Success(channels),
                    onChannelClick = { login, _ -> clicked = login },
                    onBackClick = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Alice").performClick()
        assertEquals("alice", clicked)
    }
}
