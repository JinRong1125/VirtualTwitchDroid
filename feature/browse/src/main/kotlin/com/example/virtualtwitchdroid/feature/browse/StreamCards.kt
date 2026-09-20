package com.example.virtualtwitchdroid.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.virtualtwitchdroid.core.designsystem.component.LiveBadge
import com.example.virtualtwitchdroid.core.designsystem.component.NetworkImage
import com.example.virtualtwitchdroid.core.designsystem.component.StreamerInfoRow
import com.example.virtualtwitchdroid.core.designsystem.component.TagChip
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.model.Channel

/** Shared live-channel card used by both the Popular list and a category's stream list. */
@Composable
internal fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            NetworkImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = Spacing.sm, horizontal = Spacing.md)
                    .clip(MaterialTheme.shapes.small),
            )
            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(vertical = 14.dp, horizontal = 18.dp),
            )
        }
        ChannelInformation(channel)
    }
}

@Composable
private fun ChannelInformation(channel: Channel) {
    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        StreamerInfoRow(name = channel.displayName, avatarUrl = channel.avatarUrl)
        if (channel.title.isNotBlank()) {
            Text(
                text = channel.title,
                modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = Spacing.xs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (channel.tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(items = channel.tags) { tag -> TagChip(text = tag) }
            }
        }
    }
}
