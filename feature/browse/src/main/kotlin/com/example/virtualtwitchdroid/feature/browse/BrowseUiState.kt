package com.example.virtualtwitchdroid.feature.browse

import androidx.annotation.StringRes
import com.example.virtualtwitchdroid.core.model.Channel

/** State of the live-channel browse list. */
sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Success(val channels: List<Channel>, val loadMore: LoadMoreState = LoadMoreState.Idle) : BrowseUiState
    data class Error(@StringRes val messageRes: Int) : BrowseUiState
}
