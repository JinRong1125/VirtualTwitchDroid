package com.example.virtualtwitchdroid.feature.publish.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.publish.PublishController
import com.example.virtualtwitchdroid.feature.publish.PublishScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the "go live to Twitch" (RTMP publish) screen (a bottom tab). */
@Serializable
data object PublishRoute

fun NavGraphBuilder.publishScreen(controller: PublishController, onBackClick: () -> Unit, onMinimize: () -> Unit) {
    composable<PublishRoute> {
        PublishScreen(controller = controller, onBackClick = onBackClick, onMinimize = onMinimize)
    }
}
