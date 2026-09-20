package com.example.virtualtwitchdroid.feature.stream.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.stream.PlayerController
import com.example.virtualtwitchdroid.feature.stream.StreamScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the stream watch screen. */
@Serializable
data class StreamRoute(val channelLogin: String, val viewerCount: Int = 0) {
    companion object {
        /** SavedStateHandle keys navigation stores the args under (the property names). */
        const val CHANNEL_LOGIN_ARG = "channelLogin"
        const val VIEWER_COUNT_ARG = "viewerCount"
    }
}

fun NavController.navigateToStream(channelLogin: String, viewerCount: Int = 0, navOptions: NavOptions? = null) =
    navigate(route = StreamRoute(channelLogin, viewerCount), navOptions = navOptions)

fun NavGraphBuilder.streamScreen(controller: PlayerController, onMinimize: () -> Unit) {
    composable<StreamRoute> {
        StreamScreen(controller = controller, onMinimize = onMinimize)
    }
}
