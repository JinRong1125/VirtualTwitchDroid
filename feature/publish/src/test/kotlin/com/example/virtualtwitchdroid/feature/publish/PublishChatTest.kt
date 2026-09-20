package com.example.virtualtwitchdroid.feature.publish

import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.ChatMessage
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/**
 * The two pure rules behind "receive chat only while live" and the rolling message buffer that the
 * transparent publish chat overlay renders — verified deterministically, without a real broadcast.
 */
class PublishChatTest {

    private fun msg(id: String) = ChatMessage(id = id, userName = "u$id", color = null, message = "m$id")

    @Test
    fun chatChannel_isReceivedOnlyWhenLiveAndNonBlank() {
        assertEquals("ninja", chatChannelWhenLive(live = true, channel = "ninja"))
        assertNull(chatChannelWhenLive(live = false, channel = "ninja")) // off-air → no socket
        assertNull(chatChannelWhenLive(live = true, channel = "")) // blank username → off
        assertNull(chatChannelWhenLive(live = true, channel = null))
    }

    @Test
    fun reduceChat_appendsMessagesAndIgnoresLifecycleEvents() {
        var acc = emptyList<ChatMessage>()
        acc = reduceChat(acc, ChatEvent.Connected, max = 100)
        assertEquals(0, acc.size)
        acc = reduceChat(acc, ChatEvent.Message(msg("1")), max = 100)
        acc = reduceChat(acc, ChatEvent.Message(msg("2")), max = 100)
        acc = reduceChat(acc, ChatEvent.Notice("followers-only"), max = 100)
        acc = reduceChat(acc, ChatEvent.Disconnected, max = 100)
        assertEquals(listOf("1", "2"), acc.map { it.id })
    }

    @Test
    fun reduceChat_capsAtMaxKeepingNewest() {
        var acc = emptyList<ChatMessage>()
        repeat(5) { acc = reduceChat(acc, ChatEvent.Message(msg("$it")), max = 3) }
        assertEquals(listOf("2", "3", "4"), acc.map { it.id })
    }
}
