package com.example.virtualtwitchdroid.core.model

/**
 * A resolved, playable live stream: the signed HLS multivariant-playlist URL that a media
 * player can consume directly.
 */
data class PlayableStream(val hlsPlaylistUrl: String)
