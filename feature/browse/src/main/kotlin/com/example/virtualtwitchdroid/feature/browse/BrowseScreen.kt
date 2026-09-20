package com.example.virtualtwitchdroid.feature.browse

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.model.Channel

@Composable
internal fun BrowseScreen(
    onChannelClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BrowseScreen(
        uiState = uiState,
        onChannelClick = onChannelClick,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}

@Composable
internal fun BrowseScreen(
    uiState: BrowseUiState,
    onChannelClick: (String, Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.live_channels),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.lg,
                bottom = Spacing.sm,
            ),
        )

        when (uiState) {
            BrowseUiState.Loading ->
                Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }

            is BrowseUiState.Error -> ErrorState(
                messageRes = uiState.messageRes,
                onRetry = onRetry,
            )

            is BrowseUiState.Success -> {
                // Xtra-style: single column in portrait, a multi-column grid in landscape.
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(if (landscape) 2 else 1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(if (landscape) Spacing.sm else Spacing.none),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(items = uiState.channels, key = { it.login }) { channel ->
                        ChannelCard(channel = channel, onClick = { onChannelClick(channel.login, channel.viewerCount) })
                    }
                    if (uiState.loadMore != LoadMoreState.Idle) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LoadMoreFooter(state = uiState.loadMore, onRetry = onLoadMore)
                        }
                    }
                }
                gridState.LoadMoreOnEnd(onLoadMore = onLoadMore)
            }
        }
    }
}

@Composable
private fun ErrorState(@StringRes messageRes: Int, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = stringResource(messageRes), color = MaterialTheme.colorScheme.onSurface)
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Preview
@Composable
private fun BrowseScreenPreview() {
    TwitchTheme {
        BrowseScreen(
            uiState = BrowseUiState.Success(
                listOf(
                    Channel(
                        "ninja",
                        "Ninja",
                        "Fortnite tournament!",
                        "Fortnite",
                        42_100,
                        null,
                        null,
                        listOf("FPS", "English"),
                    ),
                    Channel(
                        "pokimane",
                        "pokimane",
                        "just chatting",
                        "Just Chatting",
                        12_345,
                        null,
                        null,
                        listOf("IRL"),
                    ),
                ),
            ),
            onChannelClick = { _, _ -> },
            onRetry = {},
        )
    }
}
