package com.example.virtualtwitchdroid.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.virtualtwitchdroid.R
import com.example.virtualtwitchdroid.core.designsystem.component.LocalMiniWindowAnchor
import com.example.virtualtwitchdroid.core.designsystem.component.MiniWindowAnchor
import com.example.virtualtwitchdroid.core.designsystem.theme.Motion
import com.example.virtualtwitchdroid.feature.avatar.navigation.AvatarRoute
import com.example.virtualtwitchdroid.feature.avatar.navigation.avatarScreen
import com.example.virtualtwitchdroid.feature.browse.navigation.BrowseRoute
import com.example.virtualtwitchdroid.feature.browse.navigation.CategoryRoute
import com.example.virtualtwitchdroid.feature.browse.navigation.GamesRoute
import com.example.virtualtwitchdroid.feature.browse.navigation.SearchRoute
import com.example.virtualtwitchdroid.feature.browse.navigation.browseScreen
import com.example.virtualtwitchdroid.feature.browse.navigation.categoryScreen
import com.example.virtualtwitchdroid.feature.browse.navigation.gamesScreen
import com.example.virtualtwitchdroid.feature.browse.navigation.navigateToCategory
import com.example.virtualtwitchdroid.feature.browse.navigation.searchScreen
import com.example.virtualtwitchdroid.feature.publish.PublishController
import com.example.virtualtwitchdroid.feature.publish.PublishMiniPlayer
import com.example.virtualtwitchdroid.feature.publish.navigation.PublishRoute
import com.example.virtualtwitchdroid.feature.publish.navigation.publishScreen
import com.example.virtualtwitchdroid.feature.stream.PlayerController
import com.example.virtualtwitchdroid.feature.stream.StreamMiniPlayer
import com.example.virtualtwitchdroid.feature.stream.navigation.navigateToStream
import com.example.virtualtwitchdroid.feature.stream.navigation.streamScreen
import kotlin.reflect.KClass

/**
 * The five bottom-tab destinations: Games (categories), Popular (Live Channels), Search (channels +
 * categories), Go Live (broadcast) and Avatar (face-tracked VRM avatar). Detail screens — a category's
 * stream list and the watch screen — are pushed on top and hide the bottom bar.
 */
private enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    GAMES(GamesRoute, GamesRoute::class, R.string.nav_games, Icons.Filled.VideogameAsset),
    POPULAR(BrowseRoute, BrowseRoute::class, R.string.nav_popular, Icons.Filled.Whatshot),
    SEARCH(SearchRoute, SearchRoute::class, R.string.nav_search, Icons.Filled.Search),
    GO_LIVE(PublishRoute, PublishRoute::class, R.string.nav_go_live, Icons.Filled.Videocam),
    AVATAR(AvatarRoute, AvatarRoute::class, R.string.nav_avatar, Icons.Filled.Face),
}

/**
 * Root composable: a single [NavHost] under a five-tab bottom bar (Games / Popular / Search / Go Live /
 * Avatar — the Avatar tab only when [avatarSupported], the device gate for the VRM renderer).
 * A floating [StreamMiniPlayer] is layered above everything; the [playerController] owns the player
 * above navigation so playback continues while minimized over the tabs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TwitchApp(
    playerController: PlayerController,
    publishController: PublishController,
    avatarSupported: Boolean = true,
) {
    val navController = rememberNavController()
    val tabs = remember(avatarSupported) { visibleTabs(avatarSupported) }

    // Pause playback when the whole app is backgrounded (system PiP keeps the activity started, so
    // this does not fire when entering PiP); resume when it returns to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> playerController.onEnterBackground()
                Lifecycle.Event.ON_START -> playerController.onEnterForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    // In system Picture-in-Picture the whole activity is the tiny window — hide ALL chrome so only
    // the current screen's PiP content (e.g. the camera) shows, never the bottom bar.
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val inSystemPip = remember(configuration) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            activity?.isInPictureInPictureMode == true
    }

    // A category's stream list is a drill-down from the Games tab, so it keeps the bottom bar (with
    // Games shown as selected). Only the fullscreen watch screen hides it.
    val onCategory = currentDestination.isOn(CategoryRoute::class)
    // The Avatar tab opens a camera through CameraX; the publish float keeps its Camera2 camera open
    // while minimized and would be evicted mid-broadcast (the live stream silently goes black). Like
    // every other camera/player entry point here, close the publish float first.
    val onAvatar = currentDestination.isOn(AvatarRoute::class)
    LaunchedEffect(onAvatar) { if (onAvatar) publishController.close() }
    // Hide the bar while the keyboard is open (only the Search tab has a text field) so a bottom-
    // pinned field can lift flush against the IME instead of floating a bar-height above it.
    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = !inSystemPip &&
        !imeVisible &&
        (onCategory || TopLevelTab.entries.any { tab -> currentDestination.isOn(tab.routeClass) })

    // Shared anchor so a focused text field (in a screen) can tell the floating mini-player where it
    // is, letting the mini dock above it / above the keyboard instead of being hidden by the IME.
    val miniWindowAnchor = remember { MiniWindowAnchor() }
    CompositionLocalProvider(LocalMiniWindowAnchor provides miniWindowAnchor) {
        Column(Modifier.fillMaxSize()) {
            // The content region sits ABOVE the bottom bar; the mini-player overlays here so it floats
            // on top of the app content and rests just above the tab bar.
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = BrowseRoute,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { slideIntoContainer(SlideDirection.Left, tween(Motion.long)) },
                    exitTransition = { slideOutOfContainer(SlideDirection.Left, tween(Motion.long)) },
                    popEnterTransition = { slideIntoContainer(SlideDirection.Right, tween(Motion.long)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.Right, tween(Motion.long)) },
                ) {
                    gamesScreen(
                        onGameClick = { gameName -> navController.navigateToCategory(gameName) },
                    )
                    categoryScreen(
                        onChannelClick = { login, viewers -> navController.navigateToStream(login, viewers) },
                        onBackClick = { navController.popBackStack() },
                    )
                    browseScreen(
                        onChannelClick = { login, viewers ->
                            // Starting a watch overrides any floating publish camera.
                            publishController.close()
                            navController.navigateToStream(login, viewers)
                        },
                    )
                    searchScreen(
                        onChannelClick = { login ->
                            publishController.close() // a search hit opens a watch — override any publish float
                            navController.navigateToStream(login, 0) // viewer count unknown from search
                        },
                        onCategoryClick = { name -> navController.navigateToCategory(name) },
                    )
                    streamScreen(
                        controller = playerController,
                        onMinimize = {
                            publishController.close() // only one mini floats at a time
                            playerController.minimize()
                            navController.popBackStack()
                        },
                    )
                    avatarScreen()
                    publishScreen(
                        controller = publishController,
                        onBackClick = { navController.switchTab(TopLevelTab.POPULAR.route) },
                        onMinimize = {
                            playerController.close() // minimizing the camera overrides the watch mini
                            publishController.minimize()
                            navController.switchTab(TopLevelTab.POPULAR.route)
                        },
                    )
                }

                // Only one floating mini-player is ever active (the callbacks above enforce it).
                StreamMiniPlayer(
                    controller = playerController,
                    onExpand = { login, viewers ->
                        publishController.close()
                        playerController.maximize()
                        navController.navigateToStream(login, viewers)
                    },
                )
                PublishMiniPlayer(
                    controller = publishController,
                    onExpand = {
                        playerController.close()
                        publishController.maximize()
                        navController.switchTab(TopLevelTab.GO_LIVE.route)
                    },
                )
            }

            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(tab.routeClass) ||
                                (tab == TopLevelTab.GAMES && onCategory),
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        }
    }
}

/** The bottom-bar tabs for this device: every tab, minus Avatar where the avatar isn't supported. */
private fun visibleTabs(avatarSupported: Boolean): List<TopLevelTab> =
    TopLevelTab.entries.filter { avatarSupported || it != TopLevelTab.AVATAR }

/** True if [this] destination is (or is nested under) the given top-level route. */
private fun NavDestination?.isOn(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

/** Switches bottom tabs, preserving each tab's own back stack + scroll state (single top). */
private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
