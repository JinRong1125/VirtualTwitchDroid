package com.example.virtualtwitchdroid.feature.stream

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.ChatMessage
import com.example.virtualtwitchdroid.core.model.PlayableStream
import com.example.virtualtwitchdroid.core.testing.repository.FakeChatRepository
import com.example.virtualtwitchdroid.core.testing.repository.FakeStreamRepository
import com.example.virtualtwitchdroid.core.testing.util.MainDispatcherRule
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class StreamViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val streamRepository = FakeStreamRepository()
    private val chatRepository = FakeChatRepository()

    // StreamRoute has a single `channelLogin` arg, so SavedStateHandle.toRoute() reads that key.
    private fun viewModel(channel: String = "monstercat") = StreamViewModel(
        savedStateHandle = SavedStateHandle(mapOf("channelLogin" to channel)),
        streamRepository = streamRepository,
        chatRepository = chatRepository,
    )

    @Test
    fun channelLogin_isReadFromRoute() {
        assertEquals("monstercat", viewModel("monstercat").channelLogin)
    }

    @Test
    fun streamUiState_whenResolved_emitsSuccessWithUrl() = runTest {
        streamRepository.result = PlayableStream("https://usher.test/m.m3u8")
        viewModel().streamUiState.test {
            // The intermediate Loading may be conflated away under an unconfined dispatcher.
            var state = awaitItem()
            if (state is StreamUiState.Loading) state = awaitItem()
            assertIs<StreamUiState.Success>(state)
            assertEquals("https://usher.test/m.m3u8", state.hlsPlaylistUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun streamUiState_whenRepositoryThrows_emitsError() = runTest {
        streamRepository.error = IOException("offline")
        viewModel().streamUiState.test {
            var state = awaitItem()
            if (state is StreamUiState.Loading) state = awaitItem()
            assertIs<StreamUiState.Error>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatUiState_accumulatesMessagesAndTracksConnection() = runTest {
        viewModel().chatUiState.test {
            assertEquals(ChatUiState(), awaitItem())

            chatRepository.send(ChatEvent.Connected)
            assertTrue(awaitItem().connected)

            chatRepository.send(ChatEvent.Message(ChatMessage("1", "Alice", "#FF0000", "hi")))
            val afterFirst = awaitItem()
            assertEquals(1, afterFirst.messages.size)
            assertEquals("Alice", afterFirst.messages.first().userName)

            chatRepository.send(ChatEvent.Message(ChatMessage("2", "Bob", null, "yo")))
            assertEquals(2, awaitItem().messages.size)

            chatRepository.send(ChatEvent.Disconnected)
            assertTrue(!awaitItem().connected)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatUiState_marksErroredOnDrop_andRetryChatReconnects() = runTest {
        val vm = viewModel()
        vm.chatUiState.test {
            // Initial connecting phase: not connected, but not yet errored (no Retry affordance).
            val initial = awaitItem()
            assertTrue(!initial.connected)
            assertTrue(!initial.errored)
            assertEquals(1, chatRepository.subscriptionCount)

            chatRepository.send(ChatEvent.Connected)
            assertTrue(awaitItem().connected)

            // A network drop surfaces as Disconnected → errored true, drives the chat Retry bar.
            chatRepository.send(ChatEvent.Disconnected)
            val dropped = awaitItem()
            assertTrue(!dropped.connected)
            assertTrue(dropped.errored)

            // Retry must genuinely RE-SUBSCRIBE the socket (a second collection), not just re-send
            // — this guards the chatRetryTrigger.flatMapLatest wiring against a no-op/mis-wired retry.
            vm.retryChat()
            assertEquals(2, chatRepository.subscriptionCount)

            // The fresh subscription's Connected clears the error.
            chatRepository.send(ChatEvent.Connected)
            val reconnected = awaitItem()
            assertTrue(reconnected.connected)
            assertTrue(!reconnected.errored)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatUiState_whenChatSocketThrows_doesNotCrashAndStaysDisconnected() = runTest {
        // Regression for the network-off crash: a chat-socket failure (SocketException) used to
        // propagate down the flow and crash the collector on the main dispatcher. The ViewModel's
        // .catch must swallow it — the flow completes normally, chat is simply Disconnected, and
        // local echo still works afterwards.
        chatRepository.error = IOException("Software caused connection abort")
        val vm = viewModel()
        vm.chatUiState.test {
            // No thrown exception here; the caught failure leaves connection false.
            assertTrue(!awaitItem().connected)

            // The rest of the pipeline survives the upstream failure.
            vm.sendDemoMessage("still alive")
            val state = awaitItem()
            assertEquals(1, state.messages.size)
            assertEquals("still alive", state.messages.first().message)
            // The caught failure also raises `errored`, which drives the chat Retry bar.
            assertTrue(state.errored)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendDemoMessage_echoesLocallyAsDemoUser() = runTest {
        val vm = viewModel()
        vm.chatUiState.test {
            assertEquals(ChatUiState(), awaitItem())
            vm.sendDemoMessage("  hi there  ")
            val state = awaitItem()
            assertEquals(1, state.messages.size)
            assertEquals("you", state.messages.first().userName)
            assertEquals("hi there", state.messages.first().message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendDemoMessage_ignoresBlank() = runTest {
        val vm = viewModel()
        vm.chatUiState.test {
            assertEquals(ChatUiState(), awaitItem())
            vm.sendDemoMessage("   ")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
