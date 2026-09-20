package com.example.virtualtwitchdroid.core.network

import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.Page
import com.example.virtualtwitchdroid.core.model.SearchResults

/**
 * Network boundary for Twitch stream playback. The public interface exposes just what the
 * data layer needs; the Retrofit-backed implementation is internal to this module.
 */
interface TwitchNetworkDataSource {
    /**
     * Resolves a signed HLS multivariant-playlist (usher) URL for a live channel.
     * @throws java.io.IOException on network failure or if the channel is offline/unavailable.
     */
    suspend fun getStreamPlaylistUrl(channelLogin: String): String

    /**
     * Fetches a page of the top live channels (Twitch browse/popular list), anonymously. Pass the
     * previous page's [Page.nextCursor] as [cursor] to page forward; null starts from the top.
     * @throws java.io.IOException on network failure.
     */
    suspend fun getTopChannels(limit: Int, cursor: String? = null): Page<Channel>

    /**
     * Fetches a page of the top games/categories (Xtra's Categories grid), anonymously. Pass the
     * previous page's [Page.nextCursor] as [cursor] to page forward; null starts from the top.
     * @throws java.io.IOException on network failure.
     */
    suspend fun getTopGames(limit: Int, cursor: String? = null): Page<GameCategory>

    /**
     * Fetches the current top live channels within one game/category, anonymously.
     * @throws java.io.IOException on network failure.
     */
    suspend fun getStreamsByGame(gameName: String, limit: Int): List<Channel>

    /**
     * Searches Twitch for channels + game categories matching [query], anonymously.
     * @throws java.io.IOException on network failure.
     */
    suspend fun search(query: String): SearchResults
}
