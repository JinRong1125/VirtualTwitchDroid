package com.example.virtualtwitchdroid.core.data.repository

import com.example.virtualtwitchdroid.core.data.testdoubles.FakeTwitchChatDataSource
import com.example.virtualtwitchdroid.core.model.ChatEvent
import com.example.virtualtwitchdroid.core.model.ChatMessage
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChatRepositoryTest {

    private val chatDataSource = FakeTwitchChatDataSource()
    private val repository = DefaultChatRepository(chatDataSource)

    @Test
    fun observeChat_normalizesChannel() = runTest {
        // The flow is cold; connect() runs when collected.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeChat("  IronMouse ").first()
        }
        chatDataSource.events.tryEmit(ChatEvent.Connected)
        job.join()
        assertEquals("ironmouse", chatDataSource.lastRequestedChannel)
    }

    @Test
    fun observeChat_forwardsEventsFromDataSource() = runTest {
        val received = mutableListOf<ChatEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeChat("chan").collect { received.add(it) }
        }

        chatDataSource.events.tryEmit(ChatEvent.Connected)
        chatDataSource.events.tryEmit(ChatEvent.Message(ChatMessage("1", "A", null, "hi")))

        assertEquals(2, received.size)
        assertEquals(ChatEvent.Connected, received[0])
        job.cancel()
    }
}
