package com.example.virtualtwitchdroid.core.model

/**
 * Events emitted by a live chat connection. Modeled as a sealed hierarchy so the UI
 * can react to connection lifecycle in addition to incoming messages.
 */
sealed interface ChatEvent {
    /** The socket connected and joined the channel. */
    data object Connected : ChatEvent

    /** A parsed chat message arrived. */
    data class Message(val message: ChatMessage) : ChatEvent

    /** A server NOTICE (e.g. "This room is now in followers-only mode."). */
    data class Notice(val text: String) : ChatEvent

    /** The socket dropped; the repository will attempt to reconnect. */
    data object Disconnected : ChatEvent
}
