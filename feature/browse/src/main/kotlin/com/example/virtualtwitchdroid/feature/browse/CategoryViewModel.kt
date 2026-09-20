package com.example.virtualtwitchdroid.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualtwitchdroid.core.data.repository.StreamsRepository
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.feature.browse.navigation.CategoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamsRepository: StreamsRepository,
) : ViewModel() {

    // Read the route arg by key (the property name) so the ViewModel stays unit-testable without
    // the Android framework, matching the pattern used by StreamViewModel.
    val gameName: String = requireNotNull(savedStateHandle[CategoryRoute.GAME_NAME_ARG]) {
        "CategoryViewModel requires a '${CategoryRoute.GAME_NAME_ARG}' argument"
    }

    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BrowseUiState> = retryTrigger
        .flatMapLatest {
            flow { emit(streamsRepository.getStreamsByGame(gameName)) }
                .map<List<Channel>, BrowseUiState> { BrowseUiState.Success(it) }
                .onStart { emit(BrowseUiState.Loading) }
                .catch { emit(BrowseUiState.Error(R.string.error_category)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowseUiState.Loading,
        )

    fun refresh() = retryTrigger.update { it + 1 }
}
