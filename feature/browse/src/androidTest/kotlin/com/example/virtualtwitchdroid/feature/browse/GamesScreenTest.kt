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

class GamesScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun success_showsCategoryNames() {
        composeTestRule.setContent {
            TwitchTheme {
                GamesScreen(
                    uiState = GamesUiState.Success(TestData.sampleGames),
                    onGameClick = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Categories").assertExists()
        composeTestRule.onNodeWithText("Just Chatting").assertExists()
        composeTestRule.onNodeWithText("League of Legends").assertExists()
    }

    @Test
    fun clickingCategory_invokesCallbackWithName() {
        var clicked: String? = null
        composeTestRule.setContent {
            TwitchTheme {
                GamesScreen(
                    uiState = GamesUiState.Success(TestData.sampleGames),
                    onGameClick = { clicked = it },
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Just Chatting").performClick()
        assertEquals("Just Chatting", clicked)
    }

    @Test
    fun error_showsMessageAndRetryTriggersCallback() {
        var retried = false
        composeTestRule.setContent {
            TwitchTheme {
                GamesScreen(
                    uiState = GamesUiState.Error(R.string.error_categories),
                    onGameClick = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Couldn't load categories.").assertExists()
        composeTestRule.onNodeWithText("Retry", substring = true).performClick()
        assertEquals(true, retried)
    }
}
