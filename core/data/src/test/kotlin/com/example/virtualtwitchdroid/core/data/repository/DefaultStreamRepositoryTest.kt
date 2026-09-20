package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.data.testdoubles.FakeTwitchNetworkDataSource
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStreamRepositoryTest {

    private val network = FakeTwitchNetworkDataSource()
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = DefaultStreamRepository(network, dispatcher)

    @Test
    fun getPlayableStream_normalizesChannelAndReturnsNetworkUrl() = runTest(dispatcher) {
        network.playlistUrl = "https://usher.test/monstercat.m3u8"

        val stream = repository.getPlayableStream("  MonsterCat ")

        assertEquals("https://usher.test/monstercat.m3u8", stream.hlsPlaylistUrl)
        // The channel is trimmed + lowercased before hitting the network.
        assertEquals("monstercat", network.lastRequestedChannel)
    }
}
