package com.example.virtualtwitchdroid.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualtwitchdroid.core.designsystem.component.NetworkImage
import com.example.virtualtwitchdroid.core.designsystem.component.StreamerInfoRow
import com.example.virtualtwitchdroid.core.designsystem.component.avoidMiniWindowWhenFocused
import com.example.virtualtwitchdroid.core.designsystem.theme.Sizing
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory

/** Route-level Search screen: collects state from [SearchViewModel] and forwards navigation. */
@Composable
internal fun SearchScreen(
    onChannelClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        uiState = uiState,
        onQueryChange = viewModel::setQuery,
        onChannelClick = onChannelClick,
        onCategoryClick = onCategoryClick,
        modifier = modifier,
    )
}

/** Stateless Search screen: a query field over a channels + categories result list. */
@Composable
internal fun SearchScreen(
    query: String,
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // Lift the bottom field above the keyboard. The host hides the bottom bar while the IME
            // is open, so the field's container reaches the window bottom and imePadding lands it
            // flush against the keyboard (no nav-bar-height gap).
            .imePadding(),
    ) {
        // Results fill the space above the field.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.md)) {
            when (uiState) {
                SearchUiState.Idle -> CenteredMessage(stringResource(R.string.search_prompt))
                SearchUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SearchUiState.Empty -> CenteredMessage(stringResource(R.string.search_no_results, query.trim()))
                is SearchUiState.Error -> CenteredMessage(stringResource(uiState.messageRes))
                is SearchUiState.Success -> SearchResultsList(
                    results = uiState.results,
                    onChannelClick = onChannelClick,
                    onCategoryClick = onCategoryClick,
                )
            }
        }
        // Search field pinned at the bottom, just above the app's bottom bar.
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .avoidMiniWindowWhenFocused() // let a floating mini-player dock above this field
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
    }
}

@Composable
private fun SearchResultsList(
    results: com.example.virtualtwitchdroid.core.model.SearchResults,
    onChannelClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (results.channels.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.channels)) }
            items(items = results.channels, key = { "c-${it.login}" }) { channel ->
                ChannelResultRow(channel, onClick = { onChannelClick(channel.login) })
            }
        }
        if (results.categories.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.categories)) }
            items(items = results.categories, key = { "g-${it.id}" }) { category ->
                CategoryResultRow(category, onClick = { onCategoryClick(category.name) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun ChannelResultRow(channel: Channel, onClick: () -> Unit) {
    StreamerInfoRow(
        name = channel.displayName,
        avatarUrl = channel.avatarUrl,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
    )
}

@Composable
private fun CategoryResultRow(category: GameCategory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(
            model = category.boxArtUrl,
            contentDescription = category.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(Sizing.lg)
                .aspectRatio(BOX_ART_ASPECT)
                .clip(MaterialTheme.shapes.small),
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Twitch box art is portrait (≈285×380). */
private const val BOX_ART_ASPECT = 285f / 380f
