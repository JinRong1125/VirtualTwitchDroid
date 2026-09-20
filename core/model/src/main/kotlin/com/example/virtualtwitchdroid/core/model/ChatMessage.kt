package com.example.virtualtwitchdroid.core.model

/**
 * A Twitch emote occurrence inside a chat message, from the IRC `emotes` tag.
 *
 * @param id the Twitch emote id (e.g. "25" for Kappa); the image is served from the emote CDN.
 * @param begin the start index of the emote code (inclusive), in Unicode **code points**.
 * @param end the end index of the emote code (inclusive), in Unicode **code points**.
 */
data class ChatEmote(val id: String, val begin: Int, val end: Int)

/**
 * A single line of Twitch chat parsed from an IRC `PRIVMSG`/`USERNOTICE`.
 *
 * @param id the message id (IRC `id` tag), or a generated fallback.
 * @param userName the sender's display name (`display-name` tag) or login.
 * @param color the sender's name color as a hex string (`#RRGGBB`), or null for default.
 * @param message the message body text.
 * @param isAction true when the message was sent with `/me` (IRC ACTION).
 * @param badges raw badge identifiers (e.g. "moderator", "subscriber"), for display as chips.
 * @param emotes Twitch emotes in [message], to render inline as images (like Xtra), in order.
 */
data class ChatMessage(
    val id: String,
    val userName: String,
    val color: String?,
    val message: String,
    val isAction: Boolean = false,
    val badges: List<String> = emptyList(),
    val emotes: List<ChatEmote> = emptyList(),
)
