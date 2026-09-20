package com.example.virtualtwitchdroid.core.network.retrofit

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RetrofitTwitchNetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var network: RetrofitTwitchNetwork

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .callFactory(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TwitchGraphQlApi::class.java)
        network = RetrofitTwitchNetwork(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getStreamPlaylistUrl_buildsUsherUrlWithTokenAndSignature() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"streamPlaybackAccessToken":{"value":"TOKENVALUE","signature":"SIG123"}}}""",
            ),
        )

        val url = network.getStreamPlaylistUrl("MonsterCat")

        assertTrue(url.startsWith("https://usher.ttvnw.net/api/v2/channel/hls/monstercat.m3u8"))
        assertTrue(url.contains("sig=SIG123"), "expected signature in $url")
        assertTrue(url.contains("token=TOKENVALUE"), "expected token in $url")
        assertTrue(url.contains("allow_source=true"))
    }

    @Test
    fun getStreamPlaylistUrl_sendsFullPersistedQueryBody() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"streamPlaybackAccessToken":{"value":"v","signature":"s"}}}""",
            ),
        )

        network.getStreamPlaylistUrl("chan")

        // encodeDefaults must be on, or the body would be near-empty and Twitch would 200 with null.
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"operationName\":\"PlaybackAccessToken\""), body)
        assertTrue(body.contains("\"sha256Hash\""), body)
        assertTrue(body.contains("\"login\":\"chan\""), body)
    }

    @Test
    fun getStreamPlaylistUrl_throwsWhenTokenMissing() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"streamPlaybackAccessToken":null}}"""))

        assertFailsWith<IOException> { network.getStreamPlaylistUrl("offline") }
    }

    @Test
    fun getTopChannels_mapsNodesAndResolvesThumbnailTemplate() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":{"streams":{"edges":[
                  {"node":{"title":"Fun stream","viewersCount":1234,
                    "previewImageURL":"https://cdn/{width}x{height}.jpg",
                    "broadcaster":{"login":"alice","displayName":"Alice",
                      "profileImageURL":"https://cdn/alice-50x50.png"},
                    "freeformTags":[{"name":"English"},{"name":"fps"}],
                    "game":{"displayName":"Chess"}}},
                  {"node":{"title":"No game","viewersCount":5,
                    "previewImageURL":null,
                    "broadcaster":{"login":"bob","displayName":"Bob"},"game":null}}
                ]}}}
                """.trimIndent(),
            ),
        )

        val channels = network.getTopChannels(30).items

        assertEquals(2, channels.size)
        val alice = channels[0]
        assertEquals("alice", alice.login)
        assertEquals("Alice", alice.displayName)
        assertEquals("Fun stream", alice.title)
        assertEquals("Chess", alice.gameName)
        assertEquals(1234, alice.viewerCount)
        assertEquals("https://cdn/640x360.jpg", alice.thumbnailUrl)
        // avatar is upsized to 150x150 and tags are mapped from freeformTags.
        assertEquals("https://cdn/alice-150x150.png", alice.avatarUrl)
        assertEquals(listOf("English", "fps"), alice.tags)
        // null game + null thumbnail + no avatar/tags handled gracefully.
        assertEquals(null, channels[1].gameName)
        assertEquals(null, channels[1].thumbnailUrl)
        assertEquals(null, channels[1].avatarUrl)
        assertTrue(channels[1].tags.isEmpty())
    }

    @Test
    fun getTopChannels_skipsNodesWithoutLogin() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"streams":{"edges":[
                  {"node":{"title":"x","viewersCount":1,"broadcaster":null,"game":null}}
                ]}}}""",
            ),
        )

        assertTrue(network.getTopChannels(30).items.isEmpty())
    }

    @Test
    fun getTopChannels_returnsNextCursorWhenMorePagesExist() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"streams":{
                  "edges":[{"cursor":"CUR1","node":{"title":"t","viewersCount":1,
                    "broadcaster":{"login":"a","displayName":"A"}}}],
                  "pageInfo":{"hasNextPage":true}
                }}}""",
            ),
        )
        assertEquals("CUR1", network.getTopChannels(30).nextCursor)

        // No more pages -> null cursor even if an edge cursor is present.
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"streams":{
                  "edges":[{"cursor":"CUR2","node":{"title":"t","viewersCount":1,
                    "broadcaster":{"login":"b","displayName":"B"}}}],
                  "pageInfo":{"hasNextPage":false}
                }}}""",
            ),
        )
        assertEquals(null, network.getTopChannels(30, cursor = "CUR1").nextCursor)
    }

    @Test
    fun getTopGames_mapsNodesAndResolvesBoxArtTemplate() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":{"games":{"edges":[
                  {"node":{"id":"509658","displayName":"Just Chatting",
                    "boxArtURL":"https://cdn/{width}x{height}.jpg","viewersCount":320000}},
                  {"node":{"id":"21779","displayName":"League of Legends",
                    "boxArtURL":"https://cdn/lol.jpg","viewersCount":210500}},
                  {"node":{"displayName":null}}
                ]}}}
                """.trimIndent(),
            ),
        )

        val games = network.getTopGames(50).items

        // Third node (no displayName) is skipped.
        assertEquals(2, games.size)
        assertEquals("509658", games[0].id)
        assertEquals("Just Chatting", games[0].name)
        assertEquals(320000, games[0].viewerCount)
        // {width}x{height} template resolved to the portrait box-art size.
        assertEquals("https://cdn/285x380.jpg", games[0].boxArtUrl)
        // A concrete (non-template) box-art URL passes through unchanged.
        assertEquals("https://cdn/lol.jpg", games[1].boxArtUrl)
    }

    @Test
    fun getTopGames_returnsNextCursorWhenMorePagesExist() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"games":{
                  "edges":[{"cursor":"GCUR1","node":{"id":"1","displayName":"A","viewersCount":1}}],
                  "pageInfo":{"hasNextPage":true}
                }}}""",
            ),
        )
        assertEquals("GCUR1", network.getTopGames(50).nextCursor)

        // No more pages -> null cursor even if an edge cursor is present.
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"games":{
                  "edges":[{"cursor":"GCUR2","node":{"id":"2","displayName":"B","viewersCount":1}}],
                  "pageInfo":{"hasNextPage":false}
                }}}""",
            ),
        )
        assertEquals(null, network.getTopGames(50, cursor = "GCUR1").nextCursor)
    }

    @Test
    fun getTopGames_sendsTheCursorVariableOnAPagedRequest() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"games":{"edges":[],"pageInfo":{"hasNextPage":false}}}}"""))

        network.getTopGames(50, cursor = "GCUR1")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"cursor\":\"GCUR1\""), body)
    }

    @Test
    fun getStreamsByGame_mapsGameStreamsAndUsesCategoryName() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":{"game":{"displayName":"Chess","streams":{"edges":[
                  {"node":{"title":"Blitz","viewersCount":42,
                    "previewImageURL":"https://cdn/x-640x360.jpg",
                    "broadcaster":{"login":"gm","displayName":"GM","profileImageURL":"https://cdn/gm-150x150.png"},
                    "freeformTags":[{"name":"English"}]}},
                  {"node":{"title":"no login","viewersCount":1,"broadcaster":null}}
                ]}}}}
                """.trimIndent(),
            ),
        )

        val channels = network.getStreamsByGame("Chess", 40)

        // Node without a broadcaster login is skipped.
        assertEquals(1, channels.size)
        assertEquals("gm", channels[0].login)
        assertEquals("GM", channels[0].displayName)
        assertEquals("Blitz", channels[0].title)
        // The category name is taken from the game's displayName.
        assertEquals("Chess", channels[0].gameName)
        assertEquals(listOf("English"), channels[0].tags)
    }

    @Test
    fun getStreamsByGame_sendsNameVariableInRawQuery() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"game":{"displayName":"Chess","streams":{"edges":[]}}}}"""))

        network.getStreamsByGame("Just Chatting", 40)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"query\""), body)
        assertTrue(body.contains("\"name\":\"Just Chatting\""), body)
    }

    @Test
    fun search_mapsChannelsAndCategories_skippingBlanksAndResolvingTemplates() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":{"searchFor":{
                  "channels":{"items":[
                    {"id":"1","login":"ninja","displayName":"Ninja","profileImageURL":"https://cdn/ninja-70x70.png"},
                    {"id":"2","login":"","displayName":"Blank"},
                    {"id":"3","login":"nobody"}
                  ]},
                  "games":{"items":[
                    {"id":"509658","displayName":"Just Chatting","boxArtURL":"https://cdn/{width}x{height}.jpg"},
                    {"displayName":null}
                  ]}
                }}}
                """.trimIndent(),
            ),
        )

        val results = network.search("ninja")

        // Blank-login channel is dropped; the one with no displayName falls back to its login.
        assertEquals(listOf("ninja", "nobody"), results.channels.map { it.login })
        val ninja = results.channels.first()
        assertEquals("Ninja", ninja.displayName)
        assertEquals("https://cdn/ninja-150x150.png", ninja.avatarUrl) // avatar upsized to 150x150
        assertEquals("", ninja.title) // a search hit is a channel identity, not a live stream
        assertEquals(0, ninja.viewerCount)
        assertEquals(null, ninja.thumbnailUrl)
        assertEquals("nobody", results.channels[1].displayName) // login fallback

        // Null-displayName category is dropped; box-art template resolved to portrait size.
        assertEquals(1, results.categories.size)
        assertEquals("Just Chatting", results.categories.first().name)
        assertEquals("509658", results.categories.first().id)
        assertEquals("https://cdn/285x380.jpg", results.categories.first().boxArtUrl)
    }

    @Test
    fun search_sendsQueryVariableInRawQuery() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"data":{"searchFor":{"channels":{"items":[]},"games":{"items":[]}}}}"""),
        )

        network.search("ninja")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"query\""), body)
        assertTrue(body.contains("\"q\":\"ninja\""), body)
    }
}
