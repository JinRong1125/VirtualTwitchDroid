package com.example.virtualtwitchdroid.core.model

/**
 * A Twitch game/category as shown in the "Games" browse grid (Xtra's Categories screen).
 *
 * @param id the Twitch game id.
 * @param name the category display name (also used to open the category's stream list).
 * @param boxArtUrl a ready-to-load box-art image URL (placeholders already resolved), or null.
 * @param viewerCount total concurrent viewers across the category.
 */
data class GameCategory(val id: String, val name: String, val boxArtUrl: String?, val viewerCount: Int)
