package com.example.virtualtwitchdroid.core.model

/**
 * Results of an anonymous Twitch search: matching [channels] (broadcaster identities — may be
 * offline, so no live title/viewers) and game [categories]. Backs the Search tab.
 */
data class SearchResults(val channels: List<Channel>, val categories: List<GameCategory>) {
    val isEmpty: Boolean get() = channels.isEmpty() && categories.isEmpty()
}
