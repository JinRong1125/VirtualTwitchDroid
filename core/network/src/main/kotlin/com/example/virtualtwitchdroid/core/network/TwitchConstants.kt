package com.example.virtualtwitchdroid.core.network

/**
 * Public Twitch endpoints and identifiers needed for an anonymous "watch + read chat" flow.
 * These are the well-known public web values — no user OAuth is involved.
 */
internal object TwitchConstants {
    /** Base URL for the GraphQL endpoint (the API method appends "gql"). */
    const val GQL_BASE_URL = "https://gql.twitch.tv/"

    /** The public Twitch web Client-ID. Sufficient for anonymous PlaybackAccessToken. */
    const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"

    /**
     * Server-registered persisted-query hash for the `PlaybackAccessToken` operation.
     * Twitch can rotate this; if it ever stops resolving, switch to sending the full
     * query text instead (see StreamPlaybackAccessToken.graphql in the Xtra reference).
     */
    const val PLAYBACK_ACCESS_TOKEN_HASH =
        "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"

    /** Server-registered persisted-query hash for the web `BrowsePage_Popular` operation. */
    const val BROWSE_POPULAR_HASH =
        "fb60a7f9b2fe8f9c9a080f41585bd4564bea9d3030f4d7cb8ab7f9e99b1cee67"

    /** Anonymous chat: connect to Twitch IRC over WebSocket. */
    const val CHAT_WEBSOCKET_URL = "wss://irc-ws.chat.twitch.tv:443"
}
