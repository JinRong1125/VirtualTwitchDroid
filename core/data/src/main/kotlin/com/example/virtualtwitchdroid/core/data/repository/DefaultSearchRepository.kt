package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.common.network.Dispatcher
import com.example.virtualtwitchdroid.core.common.network.TwitchDispatchers.IO
import com.example.virtualtwitchdroid.core.model.SearchResults
import com.example.virtualtwitchdroid.core.network.TwitchNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class DefaultSearchRepository @Inject constructor(
    private val network: TwitchNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun search(query: String): SearchResults = withContext(ioDispatcher) { network.search(query) }
}
