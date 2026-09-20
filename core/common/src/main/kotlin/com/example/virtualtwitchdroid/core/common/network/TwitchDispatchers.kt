package com.example.virtualtwitchdroid.core.common.network

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Qualifier for injecting a specific [kotlinx.coroutines.CoroutineDispatcher], following
 * Now in Android's convention of never hard-coding `Dispatchers.IO` at a call site.
 */
@Qualifier
@Retention(RUNTIME)
annotation class Dispatcher(val dispatcher: TwitchDispatchers)

enum class TwitchDispatchers {
    Default,
    IO,
}
