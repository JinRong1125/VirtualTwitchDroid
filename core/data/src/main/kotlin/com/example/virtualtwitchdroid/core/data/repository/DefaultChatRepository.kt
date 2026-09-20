package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.network.TwitchChatDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultChatRepository @Inject constructor(private val chatDataSource: TwitchChatDataSource) :
    ChatRepository {

    override fun observeChat(channelLogin: String): Flow<ChatEvent> =
        chatDataSource.connect(channelLogin.trim().lowercase())
}
