package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.model.ChatMessage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class ComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun liveBadge_showsLive() {
        rule.setContent { TwitchTheme { LiveBadge() } }
        rule.onNodeWithText("Live").assertExists()
    }

    @Test
    fun tagChip_showsText() {
        rule.setContent { TwitchTheme { TagChip("English") } }
        rule.onNodeWithText("English").assertExists()
    }

    @Test
    fun followButton_togglesLabelAndReportsClick() {
        var clicked = false
        rule.setContent { TwitchTheme { FollowButton(following = false, onClick = { clicked = true }) } }
        rule.onNodeWithText("Follow").performClick()
        assertTrue(clicked)
    }

    @Test
    fun followButton_showsFollowingWhenFollowed() {
        rule.setContent { TwitchTheme { FollowButton(following = true, onClick = {}) } }
        rule.onNodeWithText("Following").assertExists()
    }

    @Test
    fun channelHeader_showsNameSubtitleAndFollow() {
        var followed = false
        rule.setContent {
            TwitchTheme {
                ChannelHeader(
                    name = "monstercat",
                    subtitle = "Watching live",
                    avatarUrl = null,
                    following = false,
                    onFollowClick = { followed = true },
                )
            }
        }
        rule.onNodeWithText("monstercat").assertExists()
        rule.onNodeWithText("Watching live").assertExists()
        rule.onNodeWithText("Follow").performClick()
        assertTrue(followed)
    }

    @Test
    fun chatMessageRow_showsNameAndMessage() {
        rule.setContent {
            TwitchTheme {
                ChatMessageRow(ChatMessage("1", "Alice", "#FF0000", "hello world"))
            }
        }
        rule.onNodeWithText("Alice", substring = true).assertExists()
        rule.onNodeWithText("hello world", substring = true).assertExists()
    }

    @Test
    fun chatMessageRow_systemMessageShownPlainly() {
        rule.setContent {
            TwitchTheme {
                ChatMessageRow(
                    ChatMessage("n", "", null, "followers-only mode"),
                    isSystem = true,
                )
            }
        }
        rule.onNodeWithText("followers-only mode").assertExists()
    }

    @Test
    fun messageInputBar_showsHintAndTrophyCount() {
        rule.setContent {
            TwitchTheme {
                MessageInputBar(
                    value = TextFieldValue(""),
                    onValueChange = {},
                    onRewardsClick = {},
                    onEmotesClick = {},
                    onSend = {},
                    rewardCount = 3,
                )
            }
        }
        rule.onNodeWithText("Send a message").assertExists()
        rule.onNodeWithText("3", substring = true).assertExists()
    }

    @Test
    fun messageInputBar_sendAppearsWithTextAndReportsClick() {
        var sent = false
        rule.setContent {
            TwitchTheme {
                MessageInputBar(
                    value = TextFieldValue("hi"),
                    onValueChange = {},
                    onRewardsClick = {},
                    onEmotesClick = {},
                    onSend = { sent = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Send").performClick()
        assertTrue(sent)
    }

    @Test
    fun messageInputBar_emotesAndRewardsButtonsReportClicks() {
        var emotes = false
        var rewards = false
        rule.setContent {
            TwitchTheme {
                MessageInputBar(
                    value = TextFieldValue(""),
                    onValueChange = {},
                    onRewardsClick = { rewards = true },
                    onEmotesClick = { emotes = true },
                    onSend = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Emotes").performClick()
        rule.onNodeWithContentDescription("Channel points").performClick()
        assertTrue(emotes)
        assertTrue(rewards)
    }

    @Test
    fun rewardsPanel_showsMultiplierAndRewardNames() {
        rule.setContent { TwitchTheme { RewardsPanel(channelName = "monstercat") } }
        rule.onNodeWithText("1.2X Multiplier").assertExists()
        rule.onNodeWithText("Unlock a Random Sub Emote").assertExists()
        rule.onNodeWithText("Become a Fan").assertExists()
    }

    @Test
    fun liveOverlay_showsLiveViewersDurationAndTogglesMute() {
        var muteToggled = false
        rule.setContent {
            TwitchTheme {
                LiveOverlay(
                    viewers = 37_300,
                    durationText = "1m 8s",
                    muted = false,
                    onToggleMute = { muteToggled = true },
                )
            }
        }
        rule.onNodeWithText("Live").assertExists()
        rule.onNodeWithText("37.3K", substring = true).assertExists()
        rule.onNodeWithText("1m 8s", substring = true).assertExists()
        rule.onNodeWithContentDescription("Mute").performClick()
        assertTrue(muteToggled)
    }

    @Test
    fun emoteGrid_selectingEmoteInsertsItsToken() {
        var selected: String? = null
        rule.setContent { TwitchTheme { EmoteGrid(onEmoteSelected = { selected = it }) } }
        rule.onNodeWithContentDescription("Kappa").performClick()
        assertEquals(":Kappa: ", selected)
    }
}
