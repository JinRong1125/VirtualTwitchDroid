package com.example.virtualtwitchdroid.feature.publish

import androidx.annotation.StringRes

/** State of the broadcast (publish-to-Twitch) flow. */
sealed interface PublishUiState {
    /** Camera preview is running; not yet broadcasting. */
    data object Idle : PublishUiState

    /** Opening the RTMP connection to Twitch ingest. */
    data object Connecting : PublishUiState

    /** Broadcasting live to Twitch. */
    data object Live : PublishUiState

    /** The connection dropped mid-broadcast; retrying (1-based [attempt]) with backoff. */
    data class Reconnecting(val attempt: Int) : PublishUiState

    /** Connection or streaming failure. */
    data class Error(@StringRes val messageRes: Int) : PublishUiState
}
