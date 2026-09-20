package com.example.virtualtwitchdroid.core.network.model

import com.example.virtualtwitchdroid.core.network.TwitchConstants
import kotlinx.serialization.Serializable

/**
 * Request body for the web `BrowsePage_Popular` persisted GraphQL query — the front-page list
 * of top live streams, sorted by viewer count. Replicated from the Xtra client.
 */
@Serializable
data class TopStreamsRequest(
    val operationName: String = "BrowsePage_Popular",
    val extensions: Extensions = Extensions(),
    val variables: Variables,
) {
    @Serializable
    data class Extensions(val persistedQuery: PersistedQuery = PersistedQuery())

    @Serializable
    data class PersistedQuery(val version: Int = 1, val sha256Hash: String = TwitchConstants.BROWSE_POPULAR_HASH)

    @Serializable
    data class Variables(
        val cursor: String? = null,
        val imageWidth: Int = 50,
        val includeCostreaming: Boolean = true,
        val limit: Int,
        val options: Options = Options(),
        val platformType: String = "all",
        val sortTypeIsRecency: Boolean = false,
    )

    @Serializable
    data class Options(
        val broadcasterLanguages: List<String> = emptyList(),
        val freeformTags: List<String> = emptyList(),
        val sort: String = "VIEWER_COUNT",
    )
}

/** Response for `BrowsePage_Popular`: `data.streams.edges[].node`. */
@Serializable
data class TopStreamsResponse(val data: Data? = null) {
    @Serializable
    data class Data(val streams: Streams? = null)

    @Serializable
    data class Streams(val edges: List<Edge> = emptyList(), val pageInfo: PageInfo? = null)

    @Serializable
    data class PageInfo(val hasNextPage: Boolean = false)

    @Serializable
    data class Edge(val node: Node? = null, val cursor: String? = null)

    @Serializable
    data class Node(
        val title: String? = null,
        val viewersCount: Int? = null,
        val previewImageURL: String? = null,
        val broadcaster: Broadcaster? = null,
        val game: Game? = null,
        val freeformTags: List<Tag> = emptyList(),
    )

    @Serializable
    data class Broadcaster(
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
    )

    @Serializable
    data class Game(val displayName: String? = null)

    @Serializable
    data class Tag(val name: String? = null)
}
