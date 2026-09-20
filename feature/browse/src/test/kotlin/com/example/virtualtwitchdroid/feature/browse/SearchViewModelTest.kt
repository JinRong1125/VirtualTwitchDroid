package com.example.virtualtwitchdroid.feature.browse

import app.cash.turbine.test
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.SearchResults
import com.example.virtualtwitchdroid.core.testing.repository.FakeSearchRepository
import com.example.virtualtwitchdroid.core.testing.util.MainDispatcherRule
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSearchRepository()
    private val viewModel = SearchViewModel(repository)

    private val channel = Channel("ninja", "Ninja", "", null, 0, null)
    private val category = GameCategory("1", "Just Chatting", null, 0)

    /** Skips the initial Idle + any Loading, returning the first settled result state. */
    private suspend fun app.cash.turbine.ReceiveTurbine<SearchUiState>.awaitSettled(): SearchUiState {
        var state = awaitItem()
        while (state is SearchUiState.Idle || state is SearchUiState.Loading) state = awaitItem()
        return state
    }

    @Test
    fun uiState_isInitiallyIdle() {
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun query_emitsSuccessWithResults() = runTest {
        repository.results = SearchResults(listOf(channel), listOf(category))
        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem()) // initial
            viewModel.setQuery("ninja")
            val state = awaitSettled()
            assertIs<SearchUiState.Success>(state)
            assertEquals(1, state.results.channels.size)
            assertEquals(1, state.results.categories.size)
            assertEquals("ninja", repository.lastQuery) // the trimmed query reached the repo
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun query_withNoMatches_emitsEmpty() = runTest {
        repository.results = SearchResults(emptyList(), emptyList())
        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())
            viewModel.setQuery("zzzq")
            assertIs<SearchUiState.Empty>(awaitSettled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun query_whenRepositoryFails_emitsError() = runTest {
        repository.error = IOException("network down")
        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())
            viewModel.setQuery("ninja")
            assertIs<SearchUiState.Error>(awaitSettled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearingQuery_returnsToIdle() = runTest {
        repository.results = SearchResults(listOf(channel), emptyList())
        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())
            viewModel.setQuery("ninja")
            assertIs<SearchUiState.Success>(awaitSettled())
            viewModel.setQuery("") // clearing the field goes back to Idle (below the min length)
            assertEquals(SearchUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
