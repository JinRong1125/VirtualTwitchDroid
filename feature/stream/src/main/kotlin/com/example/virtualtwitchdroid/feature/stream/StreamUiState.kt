package com.example.virtualtwitchdroid.feature.stream

import androidx.annotation.StringRes
import com.example.virtualtwitchdroid.core.model.ChatMessage

/** State of resolving + playing the live stream. */
sealed interface StreamUiState {
    data object Loading : StreamUiState

    /** The signed HLS playlist URL is ready to hand to the player. */
    data class Success(val hlsPlaylistUrl: String) : StreamUiState

    /** The channel is offline/unknown or the network failed. */
    data class Error(@StringRes val messageRes: Int) : StreamUiState
}

/** State of the live chat pane: connection status + the rolling message buffer. */
data class ChatUiState(
    val connected: Boolean = false,
    /**
     * True once the socket has dropped (e.g. the network went off) and hasn't reconnected.
     * Distinct from `!connected`: the initial connecting phase is `!connected && !errored`, so
     * the "Chat disconnected / Retry" affordance only shows after a real drop, not on first load.
     */
    val errored: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
)
