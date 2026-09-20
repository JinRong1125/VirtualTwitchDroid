package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.common.network.Dispatcher
import com.example.virtualtwitchdroid.core.common.network.TwitchDispatchers.IO
import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.Page
import com.example.virtualtwitchdroid.core.network.TwitchNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class DefaultStreamsRepository @Inject constructor(
    private val network: TwitchNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : StreamsRepository {

    override suspend fun getTopChannels(limit: Int, cursor: String?): Page<Channel> =
        withContext(ioDispatcher) { network.getTopChannels(limit, cursor) }

    override suspend fun getTopGames(limit: Int, cursor: String?): Page<GameCategory> =
        withContext(ioDispatcher) { network.getTopGames(limit, cursor) }

    override suspend fun getStreamsByGame(gameName: String, limit: Int): List<Channel> =
        withContext(ioDispatcher) { network.getStreamsByGame(gameName, limit) }
}
