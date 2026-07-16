package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BufferedMessengerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /** A live transport that records the payloads forwarded to it, in order. */
    private class Recorder : JetWhaleMessenger {
        override val payloadFormat: StringFormat = Json
        val received: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()

        override fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean {
            received += payload
            return true
        }

        override suspend fun requestRaw(messageType: String, payload: String, timeout: kotlin.time.Duration?): String = "reply:$payload"
    }

    private fun messenger(capacity: Int) = BufferedMessenger(scope, Json, bufferCapacity = capacity)

    private suspend fun awaitSize(recorder: Recorder, size: Int) {
        withTimeout(5_000) { while (recorder.received.size < size) delay(10) }
    }

    @Test
    fun `queued events while offline flush in order once flushing opens`() = runBlocking {
        val bm = messenger(capacity = 16)
        repeat(5) { bm.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bm.bind(live)
        bm.startFlush()

        awaitSize(live, 5)
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), live.received.toList())
    }

    @Test
    fun `buffered events are held until startFlush opens the gate`() = runBlocking {
        val bm = messenger(capacity = 16)
        repeat(3) { bm.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bm.bind(live)
        delay(100) // bound, but flushing not opened yet
        assertTrue(live.received.isEmpty(), "must not flush before startFlush (init phase)")

        bm.startFlush()
        awaitSize(live, 3)
        assertEquals(listOf("p0", "p1", "p2"), live.received.toList())
    }

    @Test
    fun `trySend (DROP) is dropped and reported while offline`() {
        val bm = messenger(capacity = 16)
        assertFalse(bm.sendRaw("t", "p", OfflineSendPolicy.DROP), "DROP should report false while offline")

        // It must not have been buffered: binding flushes nothing.
        val live = Recorder()
        bm.bind(live)
        assertTrue(live.received.isEmpty())
    }

    @Test
    fun `sendOrFail (FAIL) throws while offline`() {
        val bm = messenger(capacity = 16)
        assertFailsWith<JetWhaleConnectionClosedException> {
            bm.sendRaw("t", "p", OfflineSendPolicy.FAIL)
        }
    }

    @Test
    fun `while bound, sends forward to the live transport`() = runBlocking {
        val bm = messenger(capacity = 16)
        val live = Recorder()
        bm.bind(live)
        bm.startFlush()

        assertTrue(bm.sendRaw("t", "drop", OfflineSendPolicy.DROP))
        bm.sendRaw("t", "queue", OfflineSendPolicy.QUEUE)

        awaitSize(live, 2)
        assertEquals(setOf("drop", "queue"), live.received.toSet())
    }

    @Test
    fun `the offline buffer is bounded and drops oldest, keeping the newest in order`() = runBlocking {
        val bm = messenger(capacity = 4)
        repeat(20) { bm.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bm.bind(live)
        bm.startFlush()

        // Wait for the tail to arrive, then let any stragglers settle.
        awaitSize(live, 4)
        delay(100)

        val received = live.received.toList()
        // Bounded: never the full 20 (one event may be held by the consumer beyond the buffer bound).
        assertTrue(received.size <= 5, "retained too many: $received")
        // Newest preserved and in FIFO order.
        assertEquals("p19", received.last())
        val indices = received.map { it.removePrefix("p").toInt() }
        assertEquals(indices.sorted(), indices, "not in FIFO order: $received")
    }

    @Test
    fun `capacity zero degrades queue to drop`() = runBlocking {
        val bm = messenger(capacity = 0)
        bm.sendRaw("t", "lost", OfflineSendPolicy.QUEUE) // no buffer: dropped

        val live = Recorder()
        bm.bind(live)
        bm.sendRaw("t", "kept", OfflineSendPolicy.DROP) // bound now: forwarded

        awaitSize(live, 1)
        delay(50)
        assertEquals(listOf("kept"), live.received.toList())
    }

    @Test
    fun `requests throw while offline and forward while bound`() = runBlocking {
        val bm = messenger(capacity = 16)
        assertFailsWith<JetWhaleConnectionClosedException> {
            bm.requestRaw("t", "payload", timeout = null)
        }

        bm.bind(Recorder())
        assertEquals("reply:payload", bm.requestRaw("t", "payload", timeout = null))
    }
}
