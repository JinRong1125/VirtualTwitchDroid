package com.example.virtualtwitchdroid.core.model

/**
 * A currently-live channel as shown in the browse list.
 *
 * @param login the channel login (used to open the stream + chat).
 * @param displayName the broadcaster's display name.
 * @param title the stream title.
 * @param gameName the category/game being streamed, if any.
 * @param viewerCount current concurrent viewers.
 * @param thumbnailUrl a ready-to-load preview image URL (placeholders already resolved), or null.
 * @param avatarUrl the broadcaster's profile image URL, or null.
 * @param tags free-form stream tags (e.g. "English", "fps"), for display as chips.
 */
data class Channel(
    val login: String,
    val displayName: String,
    val title: String,
    val gameName: String?,
    val viewerCount: Int,
    val thumbnailUrl: String?,
    val avatarUrl: String? = null,
    val tags: List<String> = emptyList(),
)
