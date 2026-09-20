package com.example.virtualtwitchdroid.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualtwitchdroid.core.data.repository.StreamsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GamesViewModel @Inject constructor(private val streamsRepository: StreamsRepository) : ViewModel() {

    private val state = MutableStateFlow<GamesUiState>(GamesUiState.Loading)
    private var cursor: String? = null
    private var endReached = false
    private var loading = false
    private var started = false

    val uiState: StateFlow<GamesUiState> = state
        .onStart {
            if (!started) {
                started = true
                loadPage(reset = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState.Loading)

    /** Reload from the top (also the error-retry entry point). */
    fun refresh() {
        cursor = null
        endReached = false
        loadPage(reset = true)
    }

    /** Append the next page — called as the grid nears its end (infinite scroll). No-op at the end. */
    fun loadMore() {
        if (!loading && !endReached && state.value is GamesUiState.Success) loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        if (loading) return
        loading = true
        if (reset) {
            state.value = GamesUiState.Loading
        } else {
            // Show a loading footer over the existing list while the next page fetches.
            (state.value as? GamesUiState.Success)?.let { state.value = it.copy(loadMore = LoadMoreState.Loading) }
        }
        viewModelScope.launch {
            runCatching { streamsRepository.getTopGames(cursor = cursor) }
                .onSuccess { page ->
                    cursor = page.nextCursor
                    endReached = page.nextCursor == null
                    val existing = if (reset) emptyList() else (state.value as? GamesUiState.Success)?.games.orEmpty()
                    // distinctBy id: pages can overlap, and duplicate grid keys crash Compose.
                    state.value = GamesUiState.Success((existing + page.items).distinctBy { it.id })
                }
                .onFailure {
                    if (reset) {
                        state.value = GamesUiState.Error(R.string.error_categories)
                    } else {
                        // Keep the loaded pages; surface a tap-to-retry footer instead of a silent stall.
                        (state.value as? GamesUiState.Success)?.let {
                            state.value = it.copy(loadMore = LoadMoreState.Error)
                        }
                    }
                }
            loading = false
        }
    }
}
