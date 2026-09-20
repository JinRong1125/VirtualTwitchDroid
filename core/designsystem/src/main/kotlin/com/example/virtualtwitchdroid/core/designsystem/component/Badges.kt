package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.virtualtwitchdroid.core.designsystem.R
import com.example.virtualtwitchdroid.core.designsystem.theme.LiveRed
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.designsystem.theme.pillCentered

/** The red "Live" badge shown on channel cards (clone: errorAccent, 4dp corners, 12sp). */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, color: Color = LiveRed, text: String = stringResource(R.string.live)) {
    Text(
        modifier = modifier
            .background(color = color, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall.pillCentered(),
    )
}

/** A purple pill tag chip (clone: primaryAccent, 16dp corners, white 10sp). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.pillCentered(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
