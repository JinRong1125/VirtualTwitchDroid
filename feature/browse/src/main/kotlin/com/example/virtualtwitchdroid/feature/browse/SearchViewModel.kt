package com.example.virtualtwitchdroid.feature.browse

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualtwitchdroid.core.data.repository.SearchRepository
import com.example.virtualtwitchdroid.core.model.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** UI state for the Search tab. */
sealed interface SearchUiState {
    /** Nothing typed yet (or too short) — show the prompt. */
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: SearchResults) : SearchUiState
    data object Empty : SearchUiState
    data class Error(@StringRes val messageRes: Int) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(private val searchRepository: SearchRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = _query
        .debounce(300.milliseconds) // wait for the typing to settle before hitting the network
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            // flatMapLatest cancels the in-flight search when the query changes
            if (q.length < MIN_QUERY) {
                flowOf(SearchUiState.Idle)
            } else {
                flow { emit(searchRepository.search(q)) }
                    .map<SearchResults, SearchUiState> { results ->
                        if (results.isEmpty) SearchUiState.Empty else SearchUiState.Success(results)
                    }
                    .onStart { emit(SearchUiState.Loading) }
                    .catch { emit(SearchUiState.Error(R.string.error_search)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState.Idle,
        )

    fun setQuery(value: String) {
        _query.value = value
    }

    private companion object {
        const val MIN_QUERY = 2
    }
}
