package com.example.virtualtwitchdroid.core.network.model

import kotlinx.serialization.Serializable

/**
 * Twitch's persisted-query hashes for the Games/Category browse operations aren't pinned here;
 * instead these use **raw GraphQL** query text (the fallback the constants file documents), which
 * the public web Client-ID resolves anonymously and which can't go stale like a rotated hash.
 */

// ---- Top games (categories) --------------------------------------------------------------------

@Serializable
data class GamesRequest(val query: String = TOP_GAMES_QUERY, val variables: Variables) {
    @Serializable
    data class Variables(val limit: Int, val cursor: String? = null)

    companion object {
        val TOP_GAMES_QUERY = """
            query TopGames(${'$'}limit: Int!, ${'$'}cursor: Cursor) {
              games(first: ${'$'}limit, after: ${'$'}cursor, options: {sort: VIEWER_COUNT}) {
                edges { cursor node { id displayName boxArtURL(width: 285, height: 380) viewersCount } }
                pageInfo { hasNextPage }
              }
            }
        """.trimIndent()
    }
}

@Serializable
data class GamesResponse(val data: Data? = null) {
    @Serializable
    data class Data(val games: Games? = null)

    @Serializable
    data class Games(val edges: List<Edge> = emptyList(), val pageInfo: PageInfo? = null)

    @Serializable
    data class PageInfo(val hasNextPage: Boolean = false)

    @Serializable
    data class Edge(val node: Node? = null, val cursor: String? = null)

    @Serializable
    data class Node(
        val id: String? = null,
        val displayName: String? = null,
        val boxArtURL: String? = null,
        val viewersCount: Int? = null,
    )
}

// ---- Streams within one game (category) --------------------------------------------------------

@Serializable
data class GameStreamsRequest(val query: String = GAME_STREAMS_QUERY, val variables: Variables) {
    @Serializable
    data class Variables(val name: String, val limit: Int)

    companion object {
        val GAME_STREAMS_QUERY = """
            query GameStreams(${'$'}name: String!, ${'$'}limit: Int!) {
              game(name: ${'$'}name) {
                displayName
                streams(first: ${'$'}limit, options: {sort: VIEWER_COUNT}) {
                  edges { node {
                    title
                    viewersCount
                    previewImageURL(width: 640, height: 360)
                    broadcaster { login displayName profileImageURL(width: 150) }
                    freeformTags { name }
                  } }
                }
              }
            }
        """.trimIndent()
    }
}

@Serializable
data class GameStreamsResponse(val data: Data? = null) {
    @Serializable
    data class Data(val game: Game? = null)

    @Serializable
    data class Game(val displayName: String? = null, val streams: Streams? = null)

    @Serializable
    data class Streams(val edges: List<Edge> = emptyList())

    @Serializable
    data class Edge(val node: Node? = null)

    @Serializable
    data class Node(
        val title: String? = null,
        val viewersCount: Int? = null,
        val previewImageURL: String? = null,
        val broadcaster: Broadcaster? = null,
        val freeformTags: List<Tag> = emptyList(),
    )

    @Serializable
    data class Broadcaster(
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
    )

    @Serializable
    data class Tag(val name: String? = null)
}
