package com.kitakkun.jetwhale.agent.sdk.messaging

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleConnectionClosedException
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleTransportMessenger
import com.kitakkun.jetwhale.protocol.messaging.RawSendOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class BufferedMessengerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /** A live transport that hands on the payloads forwarded to it, in order. */
    private class Recorder : JetWhaleTransportMessenger {
        override val payloadFormat: StringFormat = Json

        /** Unlimited so that forwarding never suspends the messenger under test. */
        val arrivals: Channel<String> = Channel(Channel.UNLIMITED)

        override fun sendRaw(messageType: String, payload: String): Boolean {
            arrivals.trySend(payload)
            return true
        }

        override fun trySendRaw(messageType: String, payload: String): RawSendOutcome {
            arrivals.trySend(payload)
            return RawSendOutcome.SENT
        }

        override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = "reply:$payload"

        suspend fun awaitPayloads(count: Int): List<String> = withTimeout(5_000) { List(count) { arrivals.receive() } }
    }

    @Test
    fun `queued events while offline flush in order once flushing opens`() = runBlocking {
        val bm = messenger(capacity = 16)
        repeat(5) { bm.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bm.bind(live)
        bm.startFlush()

        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), live.awaitPayloads(5))
    }

    private fun messenger(capacity: Int) = BufferedMessenger(scope, Json, bufferCapacity = capacity)

    // The claim is that nothing flushes while the gate is shut, and the messenger offers no signal
    // for "the flusher has had its turn and stayed parked" — only elapsed time can establish it.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `buffered events are held until startFlush opens the gate`() = runBlocking {
        val bm = messenger(capacity = 16)
        repeat(3) { bm.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bm.bind(live)
        delay(100) // bound, but flushing not opened yet
        assertTrue(live.arrivals.tryReceive().isFailure, "must not flush before startFlush (init phase)")

        bm.startFlush()
        assertEquals(listOf("p0", "p1", "p2"), live.awaitPayloads(3))
    }

    @Test
    fun `trySend (DROP) is dropped and reported while offline`() {
        val bm = messenger(capacity = 16)
        assertFalse(bm.sendRaw("t", "p", OfflineSendPolicy.DROP), "DROP should report false while offline")

        // It must not have been buffered: binding flushes nothing.
        val live = Recorder()
        bm.bind(live)
        assertTrue(live.arrivals.tryReceive().isFailure)
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

        assertEquals(setOf("drop", "queue"), live.awaitPayloads(2).toSet())
    }

    @Test
    fun `the offline buffer is bounded and drops oldest, keeping the newest in order`() = runBlocking {
        val bm = messenger(capacity = 4)
        repeat(20) { bm.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bm.bind(live)
        bm.startFlush()

        // Once the bound's worth has arrived the buffer has room again, so a sentinel queued now
        // cannot evict anything and — the buffer being FIFO with a single consumer — arrives last.
        // Its arrival is what marks the flush complete, with no straggler left to come.
        val received = buildList {
            addAll(live.awaitPayloads(4))
            bm.sendRaw("t", "end", OfflineSendPolicy.QUEUE)
            while (true) {
                val payload = live.awaitPayloads(1).single()
                if (payload == "end") break
                add(payload)
            }
        }

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

        // With no buffer there is nothing "lost" could be held in, so nothing can follow "kept".
        assertEquals(listOf("kept"), live.awaitPayloads(1))
        assertTrue(live.arrivals.tryReceive().isFailure)
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
