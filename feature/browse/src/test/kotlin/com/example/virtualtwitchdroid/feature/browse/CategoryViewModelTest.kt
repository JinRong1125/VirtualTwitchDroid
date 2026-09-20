package com.example.virtualtwitchdroid.feature.browse

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.virtualtwitchdroid.core.testing.data.TestData
import com.example.virtualtwitchdroid.core.testing.repository.FakeStreamsRepository
import com.example.virtualtwitchdroid.core.testing.util.MainDispatcherRule
import com.example.virtualtwitchdroid.feature.browse.navigation.CategoryRoute
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CategoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreamsRepository()

    // CategoryRoute stores its arg under the property name; the ViewModel reads it by key.
    private fun viewModel(game: String = "Chess") = CategoryViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CategoryRoute.GAME_NAME_ARG to game)),
        streamsRepository = repository,
    )

    @Test
    fun gameName_isReadFromRoute() {
        assertEquals("Chess", viewModel("Chess").gameName)
    }

    @Test
    fun uiState_emitsSuccessWithChannelsForTheGame() = runTest {
        repository.gameChannels = TestData.sampleChannels
        viewModel().uiState.test {
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
        repository.error = IOException("offline")
        viewModel().uiState.test {
            var state = awaitItem()
            if (state is BrowseUiState.Loading) state = awaitItem()
            assertIs<BrowseUiState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
