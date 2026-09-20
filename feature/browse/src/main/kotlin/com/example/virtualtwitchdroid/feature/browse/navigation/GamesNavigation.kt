package com.example.virtualtwitchdroid.feature.browse.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.browse.GamesScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the Games/Categories grid tab. */
@Serializable
data object GamesRoute

fun NavGraphBuilder.gamesScreen(onGameClick: (String) -> Unit) {
    composable<GamesRoute> {
        GamesScreen(onGameClick = onGameClick)
    }
}
