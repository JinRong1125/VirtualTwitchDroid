package com.example.virtualtwitchdroid.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing

/** Status of an append (next-page) load for an infinite-scroll grid. */
enum class LoadMoreState { Idle, Loading, Error }

/**
 * Footer for a paginating grid: a spinner while the next page loads, or a tap-to-retry row when it
 * failed. Rendered as a full-span grid item so a transient page-append failure is neither silent nor
 * a dead end (the auto-scroll trigger will not re-fire while the list has not grown).
 */
@Composable
internal fun LoadMoreFooter(state: LoadMoreState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        LoadMoreState.Idle -> Unit
        LoadMoreState.Loading -> Box(
            modifier.fillMaxWidth().padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        LoadMoreState.Error -> Box(
            modifier.fillMaxWidth().clickable(onClick = onRetry).padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.load_more_retry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
