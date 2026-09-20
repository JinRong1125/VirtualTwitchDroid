package com.example.virtualtwitchdroid.feature.browse

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Infinite scroll: invokes [onLoadMore] once this grid scrolls within [buffer] items of the end.
 * `derivedStateOf` keeps the scroll reads off recomposition; the ViewModel de-dupes/guards the call.
 */
@Composable
internal fun LazyGridState.LoadMoreOnEnd(buffer: Int = 4, onLoadMore: () -> Unit) {
    val reached by remember(this) {
        derivedStateOf {
            val info = layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - buffer
        }
    }
    LaunchedEffect(reached) { if (reached) onLoadMore() }
}
