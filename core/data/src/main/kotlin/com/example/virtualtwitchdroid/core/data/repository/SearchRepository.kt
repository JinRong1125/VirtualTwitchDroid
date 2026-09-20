package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.model.SearchResults

/** Searches Twitch for channels + game categories (anonymous). Backs the Search tab. */
interface SearchRepository {
    suspend fun search(query: String): SearchResults
}
