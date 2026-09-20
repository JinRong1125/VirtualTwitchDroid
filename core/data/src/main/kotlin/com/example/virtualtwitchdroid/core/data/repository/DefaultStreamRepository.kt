package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.common.network.Dispatcher
import com.example.virtualtwitchdroid.core.common.network.TwitchDispatchers.IO
import com.example.virtualtwitchdroid.core.model.PlayableStream
import com.example.virtualtwitchdroid.core.network.TwitchNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class DefaultStreamRepository @Inject constructor(
    private val network: TwitchNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : StreamRepository {

    override suspend fun getPlayableStream(channelLogin: String): PlayableStream = withContext(ioDispatcher) {
        val login = channelLogin.trim().lowercase()
        PlayableStream(hlsPlaylistUrl = network.getStreamPlaylistUrl(login))
    }
}
