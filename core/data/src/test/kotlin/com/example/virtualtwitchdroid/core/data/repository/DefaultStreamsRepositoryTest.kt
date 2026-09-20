package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.data.testdoubles.FakeTwitchNetworkDataSource
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStreamsRepositoryTest {

    private val network = FakeTwitchNetworkDataSource()
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = DefaultStreamsRepository(network, dispatcher)

    @Test
    fun getTopChannels_delegatesToNetworkWithLimit() = runTest(dispatcher) {
        network.topChannels = listOf(
            Channel("a", "A", "t", null, 1, null),
            Channel("b", "B", "t", null, 2, null),
        )

        val result = repository.getTopChannels(limit = 10)

        assertEquals(2, result.items.size)
        assertEquals("a", result.items.first().login)
        assertEquals(10, network.lastRequestedLimit)
    }

    @Test
    fun getTopGames_delegatesToNetworkWithLimit() = runTest(dispatcher) {
        network.topGames = listOf(
            GameCategory("1", "Just Chatting", null, 100),
            GameCategory("2", "Chess", null, 50),
        )

        val result = repository.getTopGames(limit = 25)

        assertEquals(2, result.items.size)
        assertEquals("Just Chatting", result.items.first().name)
        assertEquals(25, network.lastRequestedLimit)
    }

    @Test
    fun getStreamsByGame_delegatesWithGameNameAndLimit() = runTest(dispatcher) {
        network.gameChannels = listOf(Channel("gm", "GM", "Blitz", "Chess", 42, null))

        val result = repository.getStreamsByGame(gameName = "Chess", limit = 15)

        assertEquals(1, result.size)
        assertEquals("gm", result.first().login)
        assertEquals("Chess", network.lastRequestedGame)
        assertEquals(15, network.lastRequestedLimit)
    }
}
