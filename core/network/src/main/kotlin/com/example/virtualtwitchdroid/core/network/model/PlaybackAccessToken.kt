package com.example.virtualtwitchdroid.core.network.model

import com.example.virtualtwitchdroid.core.network.TwitchConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for the `PlaybackAccessToken` persisted GraphQL query, replicating the
 * canonical Twitch web operation used by the Xtra client.
 */
@Serializable
data class PlaybackAccessTokenRequest(
    val operationName: String = "PlaybackAccessToken",
    val extensions: Extensions = Extensions(),
    val variables: Variables,
) {
    @Serializable
    data class Extensions(val persistedQuery: PersistedQuery = PersistedQuery())

    @Serializable
    data class PersistedQuery(
        val version: Int = 1,
        val sha256Hash: String = TwitchConstants.PLAYBACK_ACCESS_TOKEN_HASH,
    )

    @Serializable
    data class Variables(
        val isLive: Boolean = true,
        val login: String,
        val isVod: Boolean = false,
        @SerialName("vodID") val vodId: String = "",
        val playerType: String = "site",
        val platform: String = "web",
    )
}

/** Response for the `PlaybackAccessToken` query: a signed token + its signature. */
@Serializable
data class PlaybackAccessTokenResponse(val data: Data? = null) {
    @Serializable
    data class Data(val streamPlaybackAccessToken: Token? = null)

    @Serializable
    data class Token(val value: String, val signature: String)
}
