package com.example.virtualtwitchdroid.feature.stream

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.model.ChatMessage
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class StreamScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val chatState = ChatUiState(
        connected = true,
        messages = listOf(
            ChatMessage("1", "Alice", "#FF0000", "hello world"),
            ChatMessage("2", "Bob", null, "waves", isAction = true),
        ),
    )

    @Test
    fun chat_rendersMessages() {
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    streamState = StreamUiState.Loading,
                    chatState = chatState,
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("hello world", substring = true).assertExists()
        composeTestRule.onNodeWithText("waves", substring = true).assertExists()
    }

    @Test
    fun messageInput_hintIsShown() {
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    streamState = StreamUiState.Loading,
                    chatState = chatState,
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Send a message").assertExists()
    }

    @Test
    fun error_showsMessageAndRetryWorks() {
        var retried = false
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    streamState = StreamUiState.Error(R.string.stream_error),
                    chatState = ChatUiState(),
                    onRetry = { retried = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Couldn't load this stream.").assertExists()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun chatErrored_showsDisconnectedBarAndRetryWorks() {
        var retriedChat = false
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    streamState = StreamUiState.Loading,
                    chatState = ChatUiState(connected = false, errored = true),
                    onRetry = {},
                    onRetryChat = { retriedChat = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Chat disconnected").assertExists()
        composeTestRule.onNodeWithText("Retry").assertHasClickAction().performClick()
        assertEquals(true, retriedChat)
    }

    @Test
    fun chatConnected_hasNoDisconnectedBar() {
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    streamState = StreamUiState.Loading,
                    chatState = chatState,
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Chat disconnected").assertDoesNotExist()
    }

    @Test
    fun playbackError_showsBlackRetryWindowAndRetryWorks() {
        var retriedPlayback = false
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    // Stream resolved fine, but the player itself errored mid-playback.
                    streamState = StreamUiState.Success("https://usher.test/m.m3u8"),
                    chatState = chatState,
                    onRetry = {},
                    playbackError = true,
                    onRetryPlayback = { retriedPlayback = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Playback stopped", substring = true).assertExists()
        composeTestRule.onNodeWithText("Retry").assertHasClickAction().performClick()
        assertEquals(true, retriedPlayback)
    }

    @Test
    fun minimizeButton_isPresent() {
        // The top-left chevron enters picture-in-picture (Xtra's minimize); it is shown while
        // the controller is visible over the (buffering) video.
        composeTestRule.setContent {
            TwitchTheme {
                StreamScreen(
                    channelLogin = "monstercat",
                    streamState = StreamUiState.Loading,
                    chatState = chatState,
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Minimize").assertExists().assertHasClickAction()
    }
}
