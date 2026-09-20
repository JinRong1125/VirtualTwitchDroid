package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.model.PlayableStream

/** Resolves a live channel into a playable stream. */
interface StreamRepository {
    suspend fun getPlayableStream(channelLogin: String): PlayableStream
}
