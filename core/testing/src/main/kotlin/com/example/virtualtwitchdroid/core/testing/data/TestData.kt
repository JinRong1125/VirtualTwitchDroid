package com.example.virtualtwitchdroid.core.testing.data

import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.ChatMessage
import com.example.virtualtwitchdroid.core.model.GameCategory

/** Shared sample data for unit and UI tests. */
object TestData {

    val sampleGames: List<GameCategory> = listOf(
        GameCategory(id = "1", name = "Just Chatting", boxArtUrl = null, viewerCount = 320_000),
        GameCategory(id = "2", name = "League of Legends", boxArtUrl = null, viewerCount = 210_500),
    )

    val sampleChannels: List<Channel> = listOf(
        Channel(
            login = "alice",
            displayName = "Alice",
            title = "Speedrunning all night",
            gameName = "Celeste",
            viewerCount = 1_234,
            thumbnailUrl = "https://cdn.test/alice-640x360.jpg",
        ),
        Channel(
            login = "bob",
            displayName = "Bob",
            title = "chill coding stream",
            gameName = "Software and Game Development",
            viewerCount = 87,
            thumbnailUrl = null,
        ),
    )

    val sampleChatMessages: List<ChatMessage> = listOf(
        ChatMessage(id = "1", userName = "Alice", color = "#FF0000", message = "hello world"),
        ChatMessage(id = "2", userName = "Bob", color = null, message = "waves", isAction = true),
    )
}
