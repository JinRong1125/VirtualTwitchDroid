package com.example.virtualtwitchdroid.feature.publish

import androidx.compose.ui.graphics.Color
import com.example.virtualtwitchdroid.core.designsystem.theme.LiveRed
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Verifies the Go Live button's "purple before live, red on live" status rule
 * ([broadcastContainerColor]) — deterministically, without a real Twitch broadcast.
 */
class BroadcastButtonColorTest {

    private val purple = Color(0xFF6441A5) // stand-in for MaterialTheme.colorScheme.primary

    @Test
    fun idle_isBrandPurple() {
        assertEquals(purple, broadcastContainerColor(isLive = false, isReconnecting = false, brandPrimary = purple))
    }

    @Test
    fun connecting_staysBrandPurple() {
        // Connecting is neither live nor reconnecting → still purple (turns red only once on-air).
        assertEquals(purple, broadcastContainerColor(isLive = false, isReconnecting = false, brandPrimary = purple))
    }

    @Test
    fun live_isRed() {
        assertEquals(LiveRed, broadcastContainerColor(isLive = true, isReconnecting = false, brandPrimary = purple))
    }

    @Test
    fun reconnecting_isRed() {
        assertEquals(LiveRed, broadcastContainerColor(isLive = false, isReconnecting = true, brandPrimary = purple))
    }
}
