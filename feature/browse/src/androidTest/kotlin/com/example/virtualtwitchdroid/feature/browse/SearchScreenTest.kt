package com.example.virtualtwitchdroid.feature.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.SearchResults
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented tests for the stateless Search screen — fed mock [SearchUiState] (no network), asserting
 * each state renders and that tapping a channel / category emits its login / name.
 */
class SearchScreenTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val channel = Channel(
        login = "ninja",
        displayName = "Ninja",
        title = "",
        gameName = null,
        viewerCount = 0,
        thumbnailUrl = null,
        avatarUrl = null, // null image URL so Coil never hits the network in a test
    )
    private val category = GameCategory(id = "509658", name = "Just Chatting", boxArtUrl = null, viewerCount = 0)

    @Test
    fun success_showsResults_andTapsEmitLoginAndName() {
        var clickedChannel: String? = null
        var clickedCategory: String? = null
        rule.setContent {
            TwitchTheme {
                SearchScreen(
                    query = "nin",
                    uiState = SearchUiState.Success(SearchResults(listOf(channel), listOf(category))),
                    onQueryChange = {},
                    onChannelClick = { clickedChannel = it },
                    onCategoryClick = { clickedCategory = it },
                )
            }
        }
        rule.onNodeWithText("Channels").assertIsDisplayed()
        rule.onNodeWithText("Categories").assertIsDisplayed()
        rule.onNodeWithText("Ninja").assertIsDisplayed()
        rule.onNodeWithText("Just Chatting").assertIsDisplayed()

        rule.onNodeWithText("Ninja").performClick()
        rule.runOnIdle { assertEquals("ninja", clickedChannel) }
        rule.onNodeWithText("Just Chatting").performClick()
        rule.runOnIdle { assertEquals("Just Chatting", clickedCategory) }
    }

    @Test
    fun idle_showsPrompt() {
        rule.setContent {
            TwitchTheme {
                SearchScreen(
                    query = "",
                    uiState = SearchUiState.Idle,
                    onQueryChange = {},
                    onChannelClick = {},
                    onCategoryClick = {},
                )
            }
        }
        rule.onNodeWithText("Search Twitch channels and categories").assertIsDisplayed()
    }

    @Test
    fun empty_showsNoResults() {
        rule.setContent {
            TwitchTheme {
                SearchScreen(
                    query = "zzzq",
                    uiState = SearchUiState.Empty,
                    onQueryChange = {},
                    onChannelClick = {},
                    onCategoryClick = {},
                )
            }
        }
        rule.onNodeWithText("No results", substring = true).assertIsDisplayed()
    }
}
