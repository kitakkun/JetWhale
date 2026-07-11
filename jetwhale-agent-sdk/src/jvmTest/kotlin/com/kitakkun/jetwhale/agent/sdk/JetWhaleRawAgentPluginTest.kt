package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalJetWhaleApi::class)
class JetWhaleRawAgentPluginTest {

    private class TestPlugin : JetWhaleRawAgentPlugin() {
        override val pluginId: String = "test"
        override val pluginVersion: String = "1.0.0"
        override suspend fun onRawMethod(message: String): String? = null
        override fun queueBufferSize(): Int = 100_000
    }

    @Test
    fun concurrentEnqueueAndActivate_losesNoMessages() {
        val plugin = TestPlugin()
        val received = Collections.synchronizedList(mutableListOf<String>())
        val threadCount = 8
        val messagesPerThread = 1_000
        val start = CountDownLatch(1)
        val workers = (0 until threadCount).map { t ->
            Thread {
                start.await()
                repeat(messagesPerThread) { i -> plugin.enqueueRawEvent("$t-$i") }
            }.apply { start() }
        }

        start.countDown()
        // Attach the sender while producers are running, so the queue flush races the enqueues.
        plugin.activate { messages -> received += messages }
        workers.forEach { it.join() }

        assertEquals(threadCount * messagesPerThread, received.size)
        assertEquals(received.size, received.toSet().size)
    }

    @Test
    fun eventsQueuedBeforeActivation_areFlushedInOrder() {
        val plugin = TestPlugin()
        plugin.enqueueRawEvent("first")
        plugin.enqueueRawEvent("second")
        val received = mutableListOf<String>()

        plugin.activate { messages -> received += messages }
        plugin.enqueueRawEvent("third")

        assertEquals(listOf("first", "second", "third"), received)
    }
}
