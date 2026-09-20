package com.example.virtualtwitchdroid.feature.browse.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.browse.BrowseScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the Browse (live channel list) screen — the app's start/home tab. */
@Serializable
data object BrowseRoute

fun NavGraphBuilder.browseScreen(onChannelClick: (String, Int) -> Unit) {
    composable<BrowseRoute> {
        BrowseScreen(onChannelClick = onChannelClick)
    }
}
