package com.example.virtualtwitchdroid.feature.browse.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.browse.SearchScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the Search tab (channels + categories). */
@Serializable
data object SearchRoute

fun NavGraphBuilder.searchScreen(onChannelClick: (String) -> Unit, onCategoryClick: (String) -> Unit) {
    composable<SearchRoute> {
        SearchScreen(onChannelClick = onChannelClick, onCategoryClick = onCategoryClick)
    }
}
