package com.example.virtualtwitchdroid.feature.browse

import androidx.annotation.StringRes
import com.example.virtualtwitchdroid.core.model.GameCategory

/** State of the Games/Categories grid. */
sealed interface GamesUiState {
    data object Loading : GamesUiState
    data class Success(val games: List<GameCategory>, val loadMore: LoadMoreState = LoadMoreState.Idle) : GamesUiState
    data class Error(@StringRes val messageRes: Int) : GamesUiState
}
