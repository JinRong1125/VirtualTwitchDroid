package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.model.ChatEvent
import kotlinx.coroutines.flow.Flow

/** Streams live chat events for a channel. */
interface ChatRepository {
    fun observeChat(channelLogin: String): Flow<ChatEvent>
}
