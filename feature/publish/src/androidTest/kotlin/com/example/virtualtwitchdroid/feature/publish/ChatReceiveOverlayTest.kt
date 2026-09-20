package com.example.virtualtwitchdroid.feature.publish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.model.ChatMessage
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose tests for the publish received-chat overlay ([ChatReceiveOverlay]) — the
 * live-only UI that ARTEMIS cannot reach on-device without a real broadcast, so it is verified here
 * with mock chat data. Covers exactly what the user asked for: history is scrollable (a new message
 * must NOT yank a reader who scrolled up back to the newest), the reader at the bottom keeps
 * following new messages even once the buffer is full, and the section is half the camera height.
 */
class ChatReceiveOverlayTest {

    @get:Rule val rule = createComposeRule()

    private fun fakeChat(range: IntRange): List<ChatMessage> = range.map {
        // Zero-padded body so a substring match (e.g. "line-001") is unambiguous across the list.
        ChatMessage(id = "$it", userName = "u$it", color = null, message = "line-%03d".format(it))
    }

    @Test
    fun scrollingUp_reachesOldChat_andNewMessageDoesNotYankReaderToBottom() {
        val messages = mutableStateOf(fakeChat(1..40))
        rule.setContent {
            TwitchTheme {
                Box(Modifier.height(400.dp)) {
                    ChatReceiveOverlay(messages = messages.value, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // History is reachable: scroll all the way back to the oldest line.
        rule.onNode(hasScrollAction()).performScrollToIndex(0)
        rule.onNodeWithText("line-001", substring = true).assertIsDisplayed()

        // A new message arrives while the reader is scrolled up reading old chat.
        rule.runOnIdle { messages.value = messages.value + fakeChat(41..41) }
        rule.waitForIdle()

        // The reader stays on the old chat they scrolled to — NOT force-scrolled to the newest.
        // (Before the auto-scroll fix this failed: every new message jumped the list to the bottom.)
        rule.onNodeWithText("line-001", substring = true).assertIsDisplayed()
    }

    @Test
    fun atBottom_keepsFollowingNewest_evenWhenBufferSizeStaysConstant() {
        val messages = mutableStateOf(fakeChat(1..40))
        rule.setContent {
            TwitchTheme {
                Box(Modifier.height(400.dp)) {
                    ChatReceiveOverlay(messages = messages.value, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        // Put the reader at the bottom (following the newest line).
        rule.onNode(hasScrollAction()).performScrollToIndex(39)
        rule.onNodeWithText("line-040", substring = true).assertIsDisplayed()

        // Simulate a saturated rolling buffer (MAX_CHAT_MESSAGES): size stays 40 — oldest dropped,
        // newest appended — so message COUNT does not change, only the newest id.
        rule.runOnIdle { messages.value = messages.value.drop(1) + fakeChat(41..41) }
        rule.waitForIdle()

        // The at-bottom reader keeps following the newest line. (Fails when the auto-scroll effect is
        // keyed on messages.size, which never changes once the buffer is full.)
        rule.onNodeWithText("line-041", substring = true).assertIsDisplayed()
    }

    @Test
    fun overlaySection_isHalfTheCameraHeight_withTheRealCallSiteModifiers() {
        var cameraHeight = 0
        var overlayHeight = 0
        rule.setContent {
            TwitchTheme {
                Box(
                    Modifier
                        .height(400.dp)
                        .fillMaxWidth()
                        .onSizeChanged { cameraHeight = it.height },
                ) {
                    // Mirror the production call site (align + full width + the bottom button clearance:
                    // the Go Live slot's measured 48 dp touch target + Spacing.md margin, + Spacing.sm);
                    // onSizeChanged is first so it measures the overlay's own (half) node, not the inset.
                    ChatReceiveOverlay(
                        messages = fakeChat(1..10),
                        modifier = Modifier
                            .onSizeChanged { overlayHeight = it.height }
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(bottom = 48.dp + Spacing.md + Spacing.sm),
                    )
                }
            }
        }
        rule.waitForIdle()

        assertTrue(cameraHeight > 0 && overlayHeight > 0, "layout sizes were not measured")
        assertTrue(
            abs(overlayHeight - cameraHeight / 2) <= 2,
            "chat overlay ($overlayHeight px) should be half the camera height ($cameraHeight px)",
        )
    }
}
