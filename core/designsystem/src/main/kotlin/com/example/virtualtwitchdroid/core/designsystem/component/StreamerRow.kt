package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.virtualtwitchdroid.core.designsystem.R
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.Sizing
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing

/** Avatar + streamer name row (clone: 24dp circle avatar, 8dp gap, titleSmall bold name). */
@Composable
fun StreamerInfoRow(name: String, avatarUrl: String?, modifier: Modifier = Modifier, avatarSize: Dp = Sizing.sm) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        NetworkImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(avatarSize).clip(CircleShape),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The purple "♥ Follow" / "Following" button. */
@Composable
fun FollowButton(following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(IconSize.xs),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = if (following) stringResource(R.string.following) else stringResource(R.string.follow),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Collapsible channel header: avatar + name + subtitle on the left, Follow button on the right. */
@Composable
fun ChannelHeader(
    name: String,
    subtitle: String,
    avatarUrl: String?,
    following: Boolean,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NetworkImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(Sizing.md).clip(CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FollowButton(following = following, onClick = onFollowClick)
    }
}
