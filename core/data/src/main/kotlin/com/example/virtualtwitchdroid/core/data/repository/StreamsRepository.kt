package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.Page

/** Provides paged lists of live channels and game categories for browsing. */
interface StreamsRepository {
    /** A page of top live channels; pass a page's [Page.nextCursor] to load the next. */
    suspend fun getTopChannels(limit: Int = 30, cursor: String? = null): Page<Channel>

    /** A page of top games/categories for the Games grid. */
    suspend fun getTopGames(limit: Int = 50, cursor: String? = null): Page<GameCategory>

    /** Top live channels within one game/category. */
    suspend fun getStreamsByGame(gameName: String, limit: Int = 40): List<Channel>
}
