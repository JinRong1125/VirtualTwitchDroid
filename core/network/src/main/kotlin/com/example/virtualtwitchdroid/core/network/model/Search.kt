package com.example.virtualtwitchdroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * Anonymous Twitch **search** over the public GraphQL `searchFor` field — resolved by the web
 * Client-ID with **raw GraphQL** (the same non-staleable approach as [GamesRequest], not a persisted
 * hash). Returns matching channels + game categories for the Search tab. Query shape verified live
 * against `gql.twitch.tv` before wiring.
 */
@Serializable
data class SearchRequest(val query: String = SEARCH_QUERY, val variables: Variables) {
    @Serializable
    data class Variables(val q: String)

    companion object {
        val SEARCH_QUERY = """
            query Search(${'$'}q: String!) {
              searchFor(userQuery: ${'$'}q, platform: "") {
                channels { items { id login displayName profileImageURL(width: 150) } }
                games { items { id displayName boxArtURL(width: 285, height: 380) } }
              }
            }
        """.trimIndent()
    }
}

@Serializable
data class SearchResponse(val data: Data? = null) {
    @Serializable
    data class Data(val searchFor: SearchFor? = null)

    @Serializable
    data class SearchFor(val channels: Channels? = null, val games: Games? = null)

    @Serializable
    data class Channels(val items: List<ChannelItem> = emptyList())

    @Serializable
    data class ChannelItem(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
    )

    @Serializable
    data class Games(val items: List<GameItem> = emptyList())

    @Serializable
    data class GameItem(val id: String? = null, val displayName: String? = null, val boxArtURL: String? = null)
}
