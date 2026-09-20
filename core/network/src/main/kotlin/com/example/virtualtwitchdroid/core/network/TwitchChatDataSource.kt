package com.example.virtualtwitchdroid.core.network

import com.example.virtualtwitchdroid.core.model.ChatEvent
import kotlinx.coroutines.flow.Flow

/**
 * Network boundary for reading a channel's live chat. Collecting the returned [Flow]
 * opens the connection; cancelling the collector closes it.
 */
interface TwitchChatDataSource {
    fun connect(channelLogin: String): Flow<ChatEvent>
}
