package com.example.virtualtwitchdroid.feature.browse

import app.cash.turbine.test
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.testing.data.TestData
import com.example.virtualtwitchdroid.core.testing.repository.FakeStreamsRepository
import com.example.virtualtwitchdroid.core.testing.util.MainDispatcherRule
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

// See BrowseViewModelTest: Turbine's default 3s wall-clock timeout flakes under the parallel unit run.
private val TURBINE_TIMEOUT = 30.seconds

class GamesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreamsRepository()
    private val viewModel = GamesViewModel(repository)

    @Test
    fun uiState_isInitiallyLoading() {
        assertEquals(GamesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_emitsLoadingThenSuccessWithGames() = runTest {
        repository.games = TestData.sampleGames
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is GamesUiState.Loading) state = awaitItem()
            assertIs<GamesUiState.Success>(state)
            assertEquals(2, state.games.size)
            assertEquals("Just Chatting", state.games.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenRepositoryFails_emitsError() = runTest {
        repository.error = IOException("network down")
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is GamesUiState.Loading) state = awaitItem()
            assertIs<GamesUiState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMore_appendsTheNextPage_threadingThePreviousCursor() = runTest {
        repository.games = listOf(GameCategory("1", "A", null, 0))
        repository.gamesNextCursor = "c1" // more pages exist
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is GamesUiState.Loading) state = awaitItem()
            assertIs<GamesUiState.Success>(state)
            assertEquals(listOf("A"), state.games.map { it.name })

            // Second page, then end of list.
            repository.games = listOf(GameCategory("2", "B", null, 0))
            repository.gamesNextCursor = null
            viewModel.loadMore()

            val appended = awaitSuccessWithGameIds(setOf("1", "2"))
            assertEquals(listOf("A", "B"), appended.games.map { it.name })
            assertEquals("c1", repository.lastGamesCursor) // page-2 fetch carries page-1's cursor
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMore_dedupesItemsThatOverlapAcrossPages() = runTest {
        repository.games = listOf(GameCategory("1", "A", null, 0))
        repository.gamesNextCursor = "c1"
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is GamesUiState.Loading) state = awaitItem()
            assertIs<GamesUiState.Success>(state)

            // Page 2 repeats id "1" and adds "2" — the repeat must not produce a duplicate key.
            repository.games = listOf(GameCategory("1", "A", null, 0), GameCategory("2", "B", null, 0))
            repository.gamesNextCursor = null
            viewModel.loadMore()

            val appended = awaitSuccessWithGameIds(setOf("1", "2"))
            assertEquals(listOf("1", "2"), appended.games.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMore_isANoOpOnceTheLastPageIsReached() = runTest {
        repository.games = listOf(GameCategory("1", "A", null, 0))
        repository.gamesNextCursor = null // first page is already the last
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is GamesUiState.Loading) state = awaitItem()
            assertIs<GamesUiState.Success>(state)

            repository.games = listOf(GameCategory("2", "B", null, 0))
            repository.gamesNextCursor = "c2"
            viewModel.loadMore()
            viewModel.loadMore()

            expectNoEvents()
            assertEquals(null, repository.lastGamesCursor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Consumes emissions (loading-footer, conflated intermediates) until the target list arrives. */
    private suspend fun app.cash.turbine.ReceiveTurbine<GamesUiState>.awaitSuccessWithGameIds(
        ids: Set<String>,
    ): GamesUiState.Success {
        while (true) {
            val item = awaitItem()
            if (item is GamesUiState.Success && item.games.map { it.id }.toSet() == ids) return item
        }
    }
}
