package com.example.virtualtwitchdroid.core.network.retrofit

import com.example.virtualtwitchdroid.core.network.model.GameStreamsRequest
import com.example.virtualtwitchdroid.core.network.model.GameStreamsResponse
import com.example.virtualtwitchdroid.core.network.model.GamesRequest
import com.example.virtualtwitchdroid.core.network.model.GamesResponse
import com.example.virtualtwitchdroid.core.network.model.PlaybackAccessTokenRequest
import com.example.virtualtwitchdroid.core.network.model.PlaybackAccessTokenResponse
import com.example.virtualtwitchdroid.core.network.model.SearchRequest
import com.example.virtualtwitchdroid.core.network.model.SearchResponse
import com.example.virtualtwitchdroid.core.network.model.TopStreamsRequest
import com.example.virtualtwitchdroid.core.network.model.TopStreamsResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/** Retrofit description of the single Twitch GraphQL call we need. */
internal interface TwitchGraphQlApi {
    @POST("gql")
    @Headers("Content-Type: application/json")
    suspend fun playbackAccessToken(
        @Header("Client-Id") clientId: String,
        @Body body: PlaybackAccessTokenRequest,
    ): PlaybackAccessTokenResponse

    @POST("gql")
    @Headers("Content-Type: application/json")
    suspend fun topStreams(@Header("Client-Id") clientId: String, @Body body: TopStreamsRequest): TopStreamsResponse

    @POST("gql")
    @Headers("Content-Type: application/json")
    suspend fun topGames(@Header("Client-Id") clientId: String, @Body body: GamesRequest): GamesResponse

    @POST("gql")
    @Headers("Content-Type: application/json")
    suspend fun gameStreams(@Header("Client-Id") clientId: String, @Body body: GameStreamsRequest): GameStreamsResponse

    @POST("gql")
    @Headers("Content-Type: application/json")
    suspend fun search(@Header("Client-Id") clientId: String, @Body body: SearchRequest): SearchResponse
}
