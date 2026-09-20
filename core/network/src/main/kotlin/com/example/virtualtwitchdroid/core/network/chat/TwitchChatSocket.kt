package com.example.virtualtwitchdroid.core.network.chat

import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.network.TwitchChatDataSource
import com.example.virtualtwitchdroid.core.network.TwitchConstants
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Reads a Twitch channel's live chat anonymously over IRC-on-WebSocket.
 *
 * Connects to [TwitchConstants.CHAT_WEBSOCKET_URL], performs the anonymous handshake
 * (`CAP REQ` for tags+commands, an anonymous `justinfan` nick with no password, then `JOIN`),
 * answers server `PING`s, and emits each parsed line as a [ChatEvent]. This is the OkHttp
 * equivalent of Xtra's hand-rolled `ChatReadWebSocket`.
 */
internal class TwitchChatSocket @Inject constructor(private val client: OkHttpClient) : TwitchChatDataSource {
    override fun connect(channelLogin: String): Flow<ChatEvent> = callbackFlow {
        val channel = channelLogin.trim().lowercase()
        val request = Request.Builder().url(TwitchConstants.CHAT_WEBSOCKET_URL).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                webSocket.send("NICK justinfan${Random.nextInt(1_000, 100_000)}")
                webSocket.send("JOIN #$channel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // A single frame can carry several \r\n-delimited IRC lines.
                text.split("\r\n").forEach { line ->
                    if (line.isBlank()) return@forEach
                    handleLine(webSocket, line)
                }
            }

            private fun handleLine(webSocket: WebSocket, line: String) {
                if (line.startsWith("PING")) {
                    webSocket.send("PONG :tmi.twitch.tv")
                    return
                }
                val irc = IrcParser.parseLine(line)
                when (irc.command) {
                    // 001 = welcome, or a successful JOIN means we're in the room.
                    "001", "JOIN" -> trySendBlocking(ChatEvent.Connected)
                    "PRIVMSG", "USERNOTICE" ->
                        trySendBlocking(ChatEvent.Message(IrcParser.toChatMessage(irc)))
                    "NOTICE" ->
                        trySendBlocking(ChatEvent.Notice(irc.params.lastOrNull().orEmpty()))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySendBlocking(ChatEvent.Disconnected)
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // A dropped network throws here (e.g. SocketException); emit Disconnected and complete
                // the flow NORMALLY. Passing `t` to close() would rethrow it to the collector — which,
                // uncaught on the main dispatcher, crashed the app when the network went off.
                trySendBlocking(ChatEvent.Disconnected)
                close()
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }
}
