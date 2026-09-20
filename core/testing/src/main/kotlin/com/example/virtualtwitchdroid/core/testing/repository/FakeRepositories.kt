package com.example.virtualtwitchdroid.core.testing.repository

import com.example.virtualtwitchdroid.core.data.repository.ChatRepository
import com.example.virtualtwitchdroid.core.data.repository.SearchRepository
import com.example.virtualtwitchdroid.core.data.repository.StreamRepository
import com.example.virtualtwitchdroid.core.data.repository.StreamsRepository
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.Page
import com.example.virtualtwitchdroid.core.model.PlayableStream
import com.example.virtualtwitchdroid.core.model.SearchResults
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/** Test double for [StreamsRepository]: returns settable pages, or throws [error] if set. */
class FakeStreamsRepository : StreamsRepository {
    var channels: List<Channel> = emptyList()
    var games: List<GameCategory> = emptyList()
    var gameChannels: List<Channel> = emptyList()

    /** Set non-null to simulate "more pages" — the returned page's nextCursor. */
    var channelsNextCursor: String? = null
    var gamesNextCursor: String? = null
    var error: Throwable? = null

    /** The cursor argument of the most recent call — lets tests assert pagination is threaded through. */
    var lastChannelsCursor: String? = null
        private set
    var lastGamesCursor: String? = null
        private set

    override suspend fun getTopChannels(limit: Int, cursor: String?): Page<Channel> {
        lastChannelsCursor = cursor
        error?.let { throw it }
        return Page(channels.take(limit), channelsNextCursor)
    }

    override suspend fun getTopGames(limit: Int, cursor: String?): Page<GameCategory> {
        lastGamesCursor = cursor
        error?.let { throw it }
        return Page(games.take(limit), gamesNextCursor)
    }

    override suspend fun getStreamsByGame(gameName: String, limit: Int): List<Channel> {
        error?.let { throw it }
        return gameChannels.take(limit)
    }
}

/** Test double for [SearchRepository]: returns settable [results], or throws [error] if set. */
class FakeSearchRepository : SearchRepository {
    var results: SearchResults = SearchResults(emptyList(), emptyList())
    var error: Throwable? = null
    var lastQuery: String? = null

    override suspend fun search(query: String): SearchResults {
        lastQuery = query
        error?.let { throw it }
        return results
    }
}

/** Test double for [StreamRepository]: returns [result] (or a derived default), or throws [error]. */
class FakeStreamRepository : StreamRepository {
    var result: PlayableStream? = null
    var error: Throwable? = null

    override suspend fun getPlayableStream(channelLogin: String): PlayableStream {
        error?.let { throw it }
        return result ?: PlayableStream("https://usher.test/$channelLogin.m3u8")
    }
}

/** Test double for [ChatRepository]: emit events into the returned flow via [send]. */
class FakeChatRepository : ChatRepository {
    private val events = MutableSharedFlow<ChatEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** When set, [observeChat] returns a flow that throws it — simulates a chat-socket failure. */
    var error: Throwable? = null

    /** Number of times the returned chat flow has been collected — asserts retry re-subscribes. */
    var subscriptionCount = 0
        private set

    override fun observeChat(channelLogin: String): Flow<ChatEvent> = (error?.let { e -> flow { throw e } } ?: events)
        .onStart { subscriptionCount++ }

    /** A test-only API to push chat events to collectors. */
    fun send(event: ChatEvent) {
        events.tryEmit(event)
    }
}
