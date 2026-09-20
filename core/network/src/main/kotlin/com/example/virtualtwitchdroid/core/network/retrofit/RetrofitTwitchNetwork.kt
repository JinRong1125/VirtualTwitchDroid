package com.example.virtualtwitchdroid.core.network.retrofit

import com.example.virtualtwitchdroid.core.model.Channel
import com.example.virtualtwitchdroid.core.model.GameCategory
import com.example.virtualtwitchdroid.core.model.Page
import com.example.virtualtwitchdroid.core.model.SearchResults
import com.example.virtualtwitchdroid.core.network.TwitchConstants
import com.example.virtualtwitchdroid.core.network.TwitchNetworkDataSource
import com.example.virtualtwitchdroid.core.network.model.GameStreamsRequest
import com.example.virtualtwitchdroid.core.network.model.GamesRequest
import com.example.virtualtwitchdroid.core.network.model.PlaybackAccessTokenRequest
import com.example.virtualtwitchdroid.core.network.model.SearchRequest
import com.example.virtualtwitchdroid.core.network.model.TopStreamsRequest
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Retrofit-backed [TwitchNetworkDataSource]. Fetches a signed `PlaybackAccessToken` via the
 * public web Client-ID (anonymous), then assembles the usher `.m3u8` URL that a media player
 * can consume directly — the same two-step flow the Xtra client uses. The [TwitchGraphQlApi]
 * is injected so tests can point it at a MockWebServer.
 */
@Singleton
internal class RetrofitTwitchNetwork @Inject constructor(private val api: TwitchGraphQlApi) : TwitchNetworkDataSource {

    override suspend fun getStreamPlaylistUrl(channelLogin: String): String {
        val login = channelLogin.trim().lowercase()
        val response = api.playbackAccessToken(
            clientId = TwitchConstants.WEB_CLIENT_ID,
            body = PlaybackAccessTokenRequest(
                variables = PlaybackAccessTokenRequest.Variables(login = login),
            ),
        )
        val token = response.data?.streamPlaybackAccessToken
            ?: throw IOException("No playback access token for '$login' (channel offline or unknown).")

        return buildUsherUrl(login, token.value, token.signature)
    }

    override suspend fun getTopChannels(limit: Int, cursor: String?): Page<Channel> {
        val response = api.topStreams(
            clientId = TwitchConstants.WEB_CLIENT_ID,
            body = TopStreamsRequest(
                variables = TopStreamsRequest.Variables(limit = limit, cursor = cursor),
            ),
        )
        val edges = response.data?.streams?.edges.orEmpty()
        val items = edges.mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val login = node.broadcaster?.login ?: return@mapNotNull null
            Channel(
                login = login,
                displayName = node.broadcaster.displayName?.takeIf { it.isNotBlank() } ?: login,
                title = node.title.orEmpty(),
                gameName = node.game?.displayName?.takeIf { it.isNotBlank() },
                viewerCount = node.viewersCount ?: 0,
                thumbnailUrl = resolveThumbnail(node.previewImageURL),
                avatarUrl = resolveAvatar(node.broadcaster.profileImageURL),
                tags = node.freeformTags.mapNotNull { it.name?.takeIf(String::isNotBlank) }.take(5),
            )
        }
        return Page(items, nextCursor(response.data?.streams?.pageInfo?.hasNextPage, edges.lastOrNull()?.cursor))
    }

    override suspend fun getTopGames(limit: Int, cursor: String?): Page<GameCategory> {
        val response = api.topGames(
            clientId = TwitchConstants.WEB_CLIENT_ID,
            body = GamesRequest(variables = GamesRequest.Variables(limit = limit, cursor = cursor)),
        )
        val edges = response.data?.games?.edges.orEmpty()
        val items = edges.mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val name = node.displayName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GameCategory(
                id = node.id?.takeIf { it.isNotBlank() } ?: name,
                name = name,
                boxArtUrl = resolveBoxArt(node.boxArtURL),
                viewerCount = node.viewersCount ?: 0,
            )
        }
        return Page(items, nextCursor(response.data?.games?.pageInfo?.hasNextPage, edges.lastOrNull()?.cursor))
    }

    /** The cursor for the next page: the last edge's cursor when the API says more pages exist. */
    private fun nextCursor(hasNextPage: Boolean?, lastCursor: String?): String? =
        if (hasNextPage == true) lastCursor else null

    override suspend fun getStreamsByGame(gameName: String, limit: Int): List<Channel> {
        val response = api.gameStreams(
            clientId = TwitchConstants.WEB_CLIENT_ID,
            body = GameStreamsRequest(
                variables = GameStreamsRequest.Variables(name = gameName, limit = limit),
            ),
        )
        val categoryName = response.data?.game?.displayName?.takeIf { it.isNotBlank() } ?: gameName
        return response.data?.game?.streams?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val login = node.broadcaster?.login ?: return@mapNotNull null
            Channel(
                login = login,
                displayName = node.broadcaster.displayName?.takeIf { it.isNotBlank() } ?: login,
                title = node.title.orEmpty(),
                gameName = categoryName,
                viewerCount = node.viewersCount ?: 0,
                thumbnailUrl = resolveThumbnail(node.previewImageURL),
                avatarUrl = resolveAvatar(node.broadcaster.profileImageURL),
                tags = node.freeformTags.mapNotNull { it.name?.takeIf(String::isNotBlank) }.take(5),
            )
        }
    }

    override suspend fun search(query: String): SearchResults {
        val response = api.search(
            clientId = TwitchConstants.WEB_CLIENT_ID,
            body = SearchRequest(variables = SearchRequest.Variables(q = query)),
        )
        val searchFor = response.data?.searchFor
        val channels = searchFor?.channels?.items.orEmpty().mapNotNull { item ->
            val login = item.login?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Channel(
                login = login,
                displayName = item.displayName?.takeIf { it.isNotBlank() } ?: login,
                title = "", // a search hit is a channel identity, not a live stream
                gameName = null,
                viewerCount = 0,
                thumbnailUrl = null,
                avatarUrl = resolveAvatar(item.profileImageURL),
            )
        }
        val categories = searchFor?.games?.items.orEmpty().mapNotNull { item ->
            val name = item.displayName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GameCategory(
                id = item.id?.takeIf { it.isNotBlank() } ?: name,
                name = name,
                boxArtUrl = resolveBoxArt(item.boxArtURL),
                viewerCount = 0,
            )
        }
        return SearchResults(channels = channels, categories = categories)
    }

    /** Resolves a game box-art `{width}x{height}` template to a concrete portrait image URL. */
    private fun resolveBoxArt(url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.contains("{width}x{height}") -> url.replace("{width}", "285").replace("{height}", "380")
        else -> url
    }

    /** Turns Twitch's `{width}x{height}` preview template into a concrete 640x360 image URL. */
    private fun resolveThumbnail(url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.contains("{width}x{height}") ->
            url.replace("{width}", "640").replace("{height}", "360")
        else -> url.replace(Regex("-\\d+x\\d+\\."), "-640x360.")
    }

    /** Upsizes the small profile image (usually 50x50) to a crisper 150x150. */
    private fun resolveAvatar(url: String?): String? =
        url?.takeIf { it.isNotBlank() }?.replace(Regex("-\\d+x\\d+\\."), "-150x150.")

    private fun buildUsherUrl(channel: String, token: String, signature: String): String {
        val p = Random.nextInt(0, 9_999_999)
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        return buildString {
            append("https://usher.ttvnw.net/api/v2/channel/hls/")
            append(channel)
            append(".m3u8")
            append("?allow_source=true")
            append("&allow_audio_only=true")
            append("&fast_bread=true") // low-latency segments
            append("&include_unavailable=true")
            append("&p=").append(p)
            append("&platform=web")
            append("&supported_codecs=h264")
            append("&sig=").append(signature)
            append("&token=").append(encodedToken)
        }
    }
}
