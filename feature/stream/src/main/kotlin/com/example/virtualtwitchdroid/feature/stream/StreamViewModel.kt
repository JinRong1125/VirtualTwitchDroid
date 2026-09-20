package com.example.virtualtwitchdroid.feature.stream

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualtwitchdroid.core.data.repository.ChatRepository
import com.example.virtualtwitchdroid.core.data.repository.StreamRepository
import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.ChatMessage
import com.example.virtualtwitchdroid.core.model.PlayableStream
import com.example.virtualtwitchdroid.feature.stream.navigation.StreamRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class StreamViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamRepository: StreamRepository,
    chatRepository: ChatRepository,
) : ViewModel() {

    // The type-safe [StreamRoute] stores its args under the property names; reading them by key
    // (rather than SavedStateHandle.toRoute) keeps this ViewModel unit-testable without the
    // Android framework, while navigation still routes via the @Serializable StreamRoute.
    val channelLogin: String = requireNotNull(savedStateHandle[StreamRoute.CHANNEL_LOGIN_ARG]) {
        "StreamViewModel requires a '${StreamRoute.CHANNEL_LOGIN_ARG}' argument"
    }

    /** Concurrent viewers as reported by the browse list; shown in the live overlay. */
    val viewerCount: Int = savedStateHandle[StreamRoute.VIEWER_COUNT_ARG] ?: 0

    private val retryTrigger = MutableStateFlow(0)
    private val chatRetryTrigger = MutableStateFlow(0)

    // Locally-echoed "demo user" messages. Real Twitch chat can't be posted to anonymously
    // (that needs a user OAuth token), so Send shows your message in your own chat view only.
    private val localMessages = MutableSharedFlow<ChatEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val streamUiState: StateFlow<StreamUiState> = retryTrigger
        .flatMapLatest {
            flow { emit(streamRepository.getPlayableStream(channelLogin)) }
                .map<PlayableStream, StreamUiState> { StreamUiState.Success(it.hlsPlaylistUrl) }
                .onStart { emit(StreamUiState.Loading) }
                .catch { emit(StreamUiState.Error(R.string.stream_error)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreamUiState.Loading,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatUiState: StateFlow<ChatUiState> =
        merge(
            // flatMapLatest lets retryChat() tear down the dropped socket and re-observe a fresh one.
            chatRetryTrigger.flatMapLatest {
                // Defense-in-depth: a chat-socket failure (e.g. network drop) must surface as
                // Disconnected, never crash the collector. Mirrors streamUiState's .catch.
                chatRepository.observeChat(channelLogin).catch { emit(ChatEvent.Disconnected) }
            },
            localMessages,
        )
            .scan(ChatUiState()) { state, event -> state.reduce(event) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ChatUiState(),
            )

    fun retry() = retryTrigger.update { it + 1 }

    /** Reconnects the live chat after a network drop (re-subscribes the chat socket). */
    fun retryChat() = chatRetryTrigger.update { it + 1 }

    /** Echoes a message into the local chat view as the demo user (not sent to Twitch). */
    fun sendDemoMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        localMessages.tryEmit(
            ChatEvent.Message(
                ChatMessage(
                    id = "local-${System.nanoTime()}",
                    userName = DEMO_USER_NAME,
                    color = DEMO_USER_COLOR,
                    message = trimmed,
                ),
            ),
        )
    }

    private fun ChatUiState.reduce(event: ChatEvent): ChatUiState = when (event) {
        ChatEvent.Connected -> copy(connected = true, errored = false)
        ChatEvent.Disconnected -> copy(connected = false, errored = true)
        is ChatEvent.Message -> copy(messages = (messages + event.message).takeLast(MAX_MESSAGES))
        is ChatEvent.Notice -> copy(
            messages = (messages + systemMessage(event.text)).takeLast(MAX_MESSAGES),
        )
    }

    private fun systemMessage(text: String) = ChatMessage(
        id = "notice-${System.nanoTime()}",
        userName = "",
        color = null,
        message = text,
        badges = listOf(SYSTEM_BADGE),
    )

    companion object {
        private const val MAX_MESSAGES = 250
        const val SYSTEM_BADGE = "system"
        private const val DEMO_USER_NAME = "you"
        private const val DEMO_USER_COLOR = "#9147FF"
    }
}
