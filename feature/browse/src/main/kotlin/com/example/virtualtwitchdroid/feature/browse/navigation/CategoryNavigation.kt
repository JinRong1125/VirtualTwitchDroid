package com.example.virtualtwitchdroid.feature.browse.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.browse.CategoryScreen
import kotlinx.serialization.Serializable

/** Type-safe route for a single game/category's live-channel list (drilled in from Games). */
@Serializable
data class CategoryRoute(val gameName: String) {
    companion object {
        /** SavedStateHandle key navigation stores the arg under (the property name). */
        const val GAME_NAME_ARG = "gameName"
    }
}

fun NavController.navigateToCategory(gameName: String, navOptions: NavOptions? = null) =
    navigate(route = CategoryRoute(gameName), navOptions = navOptions)

fun NavGraphBuilder.categoryScreen(onChannelClick: (String, Int) -> Unit, onBackClick: () -> Unit) {
    composable<CategoryRoute> {
        CategoryScreen(onChannelClick = onChannelClick, onBackClick = onBackClick)
    }
}
