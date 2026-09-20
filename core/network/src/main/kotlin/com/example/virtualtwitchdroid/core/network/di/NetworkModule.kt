package com.example.virtualtwitchdroid.core.network.di

import com.example.virtualtwitchdroid.core.network.BuildConfig
import com.example.virtualtwitchdroid.core.network.TwitchChatDataSource
import com.example.virtualtwitchdroid.core.network.TwitchConstants
import com.example.virtualtwitchdroid.core.network.TwitchNetworkDataSource
import com.example.virtualtwitchdroid.core.network.chat.TwitchChatSocket
import com.example.virtualtwitchdroid.core.network.retrofit.RetrofitTwitchNetwork
import com.example.virtualtwitchdroid.core.network.retrofit.TwitchGraphQlApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {

    @Binds
    abstract fun bindsTwitchNetworkDataSource(impl: RetrofitTwitchNetwork): TwitchNetworkDataSource

    @Binds
    abstract fun bindsTwitchChatDataSource(impl: TwitchChatSocket): TwitchChatDataSource

    companion object {
        @Provides
        @Singleton
        fun providesNetworkJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            // Twitch's persisted GraphQL query requires the full request body — operationName,
            // the persistedQuery hash and every variable. kotlinx.serialization omits fields
            // equal to their default unless this is enabled, which would send a near-empty body.
            encodeDefaults = true
        }

        @Provides
        @Singleton
        fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    if (BuildConfig.DEBUG) setLevel(HttpLoggingInterceptor.Level.BASIC)
                },
            )
            // Keep the chat WebSocket alive across quiet periods.
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        @Provides
        @Singleton
        fun providesCallFactory(client: OkHttpClient): Call.Factory = client

        @Provides
        @Singleton
        fun providesTwitchGraphQlApi(
            networkJson: Json,
            // Lazy Call.Factory so OkHttp isn't constructed on the main thread.
            callFactory: dagger.Lazy<Call.Factory>,
        ): TwitchGraphQlApi = Retrofit.Builder()
            .baseUrl(TwitchConstants.GQL_BASE_URL)
            .callFactory { callFactory.get().newCall(it) }
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TwitchGraphQlApi::class.java)
    }
}
