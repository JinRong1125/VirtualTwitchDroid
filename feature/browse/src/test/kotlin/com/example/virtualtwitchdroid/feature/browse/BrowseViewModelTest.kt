package com.example.virtualtwitchdroid.feature.browse

import app.cash.turbine.test
import com.example.virtualtwitchdroid.core.model.Channel
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

// Turbine's default 3s is WALL-clock: under the repo-wide parallel unit run, first-test JIT/class
// loading can exceed it and flake. Generous here; the tests are still deterministic (virtual time).
private val TURBINE_TIMEOUT = 30.seconds

class BrowseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreamsRepository()
    private val viewModel = BrowseViewModel(repository)

    @Test
    fun uiState_isInitiallyLoading() {
        assertEquals(BrowseUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_emitsLoadingThenSuccessWithChannels() = runTest {
        repository.channels = TestData.sampleChannels
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            // The intermediate Loading may be conflated away under an unconfined dispatcher.
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Success>(state)
            assertEquals(2, state.channels.size)
            assertEquals("alice", state.channels.first().login)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_whenRepositoryFails_emitsError() = runTest {
        repository.error = IOException("network down")
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMore_appendsTheNextPage_threadingThePreviousCursor() = runTest {
        repository.channels = listOf(Channel("alice", "Alice", "", null, 0, null))
        repository.channelsNextCursor = "c1" // more pages exist
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Success>(state)
            assertEquals(listOf("alice"), state.channels.map { it.login })

            repository.channels = listOf(Channel("bob", "Bob", "", null, 0, null))
            repository.channelsNextCursor = null // end of list
            viewModel.loadMore()

            val appended = awaitSuccessWithChannels(setOf("alice", "bob"))
            assertEquals(listOf("alice", "bob"), appended.channels.map { it.login })
            // The page-2 fetch must carry page-1's nextCursor, not start over at null.
            assertEquals("c1", repository.lastChannelsCursor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMore_dedupesItemsThatOverlapAcrossPages() = runTest {
        repository.channels = listOf(Channel("alice", "Alice", "", null, 0, null))
        repository.channelsNextCursor = "c1"
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Success>(state)

            // Page 2 repeats "alice" and adds "bob" — the repeat must not produce a duplicate key.
            repository.channels = listOf(
                Channel("alice", "Alice", "", null, 0, null),
                Channel("bob", "Bob", "", null, 0, null),
            )
            repository.channelsNextCursor = null
            viewModel.loadMore()

            val appended = awaitSuccessWithChannels(setOf("alice", "bob"))
            assertEquals(listOf("alice", "bob"), appended.channels.map { it.login })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMore_isANoOpOnceTheLastPageIsReached() = runTest {
        repository.channels = listOf(Channel("alice", "Alice", "", null, 0, null))
        repository.channelsNextCursor = null // first page is already the last
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Success>(state)

            // Any further loadMore must not fetch or emit again.
            repository.channels = listOf(Channel("bob", "Bob", "", null, 0, null))
            repository.channelsNextCursor = "c2"
            viewModel.loadMore()
            viewModel.loadMore()

            expectNoEvents()
            assertEquals(null, repository.lastChannelsCursor) // only the initial page-1 fetch happened
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refresh_reloadsFromTheTopWithANullCursor() = runTest {
        repository.channels = listOf(Channel("alice", "Alice", "", null, 0, null))
        repository.channelsNextCursor = "c1"
        viewModel.uiState.test(timeout = TURBINE_TIMEOUT) {
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Success>(state)

            repository.channels = listOf(Channel("carol", "Carol", "", null, 0, null))
            repository.channelsNextCursor = null
            viewModel.refresh()

            val reloaded = awaitSuccessWithChannels(setOf("carol"))
            // Replaced from the top, not appended, and the reset threads a null cursor.
            assertEquals(listOf("carol"), reloaded.channels.map { it.login })
            assertEquals(null, repository.lastChannelsCursor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Consumes emissions (loading-footer, conflated intermediates) until the target list arrives. */
    private suspend fun app.cash.turbine.ReceiveTurbine<BrowseUiState>.awaitSuccessWithChannels(
        logins: Set<String>,
    ): BrowseUiState.Success {
        while (true) {
            val item = awaitItem()
            if (item is BrowseUiState.Success && item.channels.map { it.login }.toSet() == logins) return item
        }
    }
}
