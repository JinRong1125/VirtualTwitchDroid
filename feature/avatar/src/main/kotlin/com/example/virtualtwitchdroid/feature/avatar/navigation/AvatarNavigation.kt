package com.example.virtualtwitchdroid.feature.avatar.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.virtualtwitchdroid.feature.avatar.AvatarScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the face-tracked avatar tab. */
@Serializable
data object AvatarRoute

fun NavGraphBuilder.avatarScreen() {
    composable<AvatarRoute> { AvatarScreen() }
}
