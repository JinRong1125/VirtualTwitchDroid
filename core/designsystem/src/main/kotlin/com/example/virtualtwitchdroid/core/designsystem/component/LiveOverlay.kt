package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.virtualtwitchdroid.core.designsystem.R
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.LiveBlue
import com.example.virtualtwitchdroid.core.designsystem.theme.Sizing
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.designsystem.theme.XtraLiveRed
import com.example.virtualtwitchdroid.core.designsystem.theme.pillCentered

/**
 * The live video overlay: a blue "Live" pill, an eye + viewer count, a red dot + elapsed
 * duration on the left, and a white circular mute toggle on the right (Stream-SDK style).
 */
@Composable
fun LiveOverlay(
    viewers: Int,
    durationText: String,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    showMute: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier
                    .background(LiveBlue, MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                text = stringResource(R.string.live),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.pillCentered(),
            )
            Spacer(Modifier.width(Spacing.md))
            Icon(
                Icons.Filled.Visibility,
                contentDescription = stringResource(R.string.viewers),
                tint = Color.White,
                modifier = Modifier.size(IconSize.sm),
            )
            Text(
                text = " ${formatViewers(viewers)}",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.width(Spacing.md))
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(XtraLiveRed),
            )
            Text(
                text = "  $durationText",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        if (showMute) {
            Surface(
                color = Color.White,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(Sizing.lg).clickable { onToggleMute() },
            ) {
                Icon(
                    imageVector = if (muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = stringResource(R.string.mute),
                    tint = Color.Black,
                    modifier = Modifier.padding(Spacing.sm).clip(CircleShape),
                )
            }
        }
    }
}

private fun formatViewers(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000f)
    else -> count.toString()
}
