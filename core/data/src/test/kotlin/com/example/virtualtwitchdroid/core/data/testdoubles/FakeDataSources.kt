package com.example.virtualtwitchdroid.core.data.testdoubles

import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.Page
import com.example.virtualtwitchdroid.core.model.SearchResults
import com.example.virtualtwitchdroid.core.network.TwitchChatDataSource
import com.example.virtualtwitchdroid.core.network.TwitchNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeTwitchNetworkDataSource : TwitchNetworkDataSource {
    var playlistUrl: String = "https://usher.test/default.m3u8"
    var topChannels: List<Channel> = emptyList()
    var topGames: List<GameCategory> = emptyList()
    var gameChannels: List<Channel> = emptyList()
    var lastRequestedChannel: String? = null
    var lastRequestedGame: String? = null
    var lastRequestedLimit: Int = -1

    override suspend fun getStreamPlaylistUrl(channelLogin: String): String {
        lastRequestedChannel = channelLogin
        return playlistUrl
    }

    var lastRequestedCursor: String? = null

    override suspend fun getTopChannels(limit: Int, cursor: String?): Page<Channel> {
        lastRequestedLimit = limit
        lastRequestedCursor = cursor
        return Page(topChannels)
    }

    override suspend fun getTopGames(limit: Int, cursor: String?): Page<GameCategory> {
        lastRequestedLimit = limit
        lastRequestedCursor = cursor
        return Page(topGames)
    }

    override suspend fun getStreamsByGame(gameName: String, limit: Int): List<Channel> {
        lastRequestedGame = gameName
        lastRequestedLimit = limit
        return gameChannels
    }

    var searchResults: SearchResults = SearchResults(emptyList(), emptyList())
    var lastSearchQuery: String? = null

    override suspend fun search(query: String): SearchResults {
        lastSearchQuery = query
        return searchResults
    }
}

class FakeTwitchChatDataSource : TwitchChatDataSource {
    val events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    var lastRequestedChannel: String? = null

    override fun connect(channelLogin: String): Flow<ChatEvent> {
        lastRequestedChannel = channelLogin
        return events
    }
}
