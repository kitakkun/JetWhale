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
        val bufferedMessenger = messenger(capacity = 16)
        repeat(5) { bufferedMessenger.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bufferedMessenger.bind(live)
        bufferedMessenger.startFlush()

        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), live.awaitPayloads(5))
    }

    private fun messenger(capacity: Int) = BufferedMessenger(scope, Json, bufferCapacity = capacity)

    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `buffered events are held until startFlush opens the gate`() = runBlocking {
        val bufferedMessenger = messenger(capacity = 16)
        repeat(3) { bufferedMessenger.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bufferedMessenger.bind(live)
        delay(100)
        assertTrue(live.arrivals.tryReceive().isFailure, "must not flush before startFlush (init phase)")

        bufferedMessenger.startFlush()
        assertEquals(listOf("p0", "p1", "p2"), live.awaitPayloads(3))
    }

    @Test
    fun `trySend (DROP) is dropped and reported while offline`() {
        val bufferedMessenger = messenger(capacity = 16)
        assertFalse(bufferedMessenger.sendRaw("t", "p", OfflineSendPolicy.DROP), "DROP should report false while offline")

        val live = Recorder()
        bufferedMessenger.bind(live)
        assertTrue(live.arrivals.tryReceive().isFailure)
    }

    @Test
    fun `sendOrFail (FAIL) throws while offline`() {
        val bufferedMessenger = messenger(capacity = 16)
        assertFailsWith<JetWhaleConnectionClosedException> {
            bufferedMessenger.sendRaw("t", "p", OfflineSendPolicy.FAIL)
        }
    }

    @Test
    fun `while bound, sends forward to the live transport`() = runBlocking {
        val bufferedMessenger = messenger(capacity = 16)
        val live = Recorder()
        bufferedMessenger.bind(live)
        bufferedMessenger.startFlush()

        assertTrue(bufferedMessenger.sendRaw("t", "drop", OfflineSendPolicy.DROP))
        bufferedMessenger.sendRaw("t", "queue", OfflineSendPolicy.QUEUE)

        assertEquals(setOf("drop", "queue"), live.awaitPayloads(2).toSet())
    }

    @Test
    fun `the offline buffer is bounded and drops oldest, keeping the newest in order`() = runBlocking {
        val bufferedMessenger = messenger(capacity = 4)
        repeat(20) { bufferedMessenger.sendRaw("t", "p$it", OfflineSendPolicy.QUEUE) }

        val live = Recorder()
        bufferedMessenger.bind(live)
        bufferedMessenger.startFlush()

        val received = buildList {
            addAll(live.awaitPayloads(4))
            bufferedMessenger.sendRaw("t", "end", OfflineSendPolicy.QUEUE)
            while (true) {
                val payload = live.awaitPayloads(1).single()
                if (payload == "end") break
                add(payload)
            }
        }

        assertTrue(received.size <= 5, "retained too many: $received")
        assertEquals("p19", received.last())
        val indices = received.map { it.removePrefix("p").toInt() }
        assertEquals(indices.sorted(), indices, "not in FIFO order: $received")
    }

    @Test
    fun `capacity zero degrades queue to drop`() = runBlocking {
        val bufferedMessenger = messenger(capacity = 0)
        bufferedMessenger.sendRaw("t", "lost", OfflineSendPolicy.QUEUE)

        val live = Recorder()
        bufferedMessenger.bind(live)
        bufferedMessenger.sendRaw("t", "kept", OfflineSendPolicy.DROP)

        assertEquals(listOf("kept"), live.awaitPayloads(1))
        assertTrue(live.arrivals.tryReceive().isFailure)
    }

    @Test
    fun `requests throw while offline and forward while bound`() = runBlocking {
        val bufferedMessenger = messenger(capacity = 16)
        assertFailsWith<JetWhaleConnectionClosedException> {
            bufferedMessenger.requestRaw("t", "payload", timeout = null)
        }

        bufferedMessenger.bind(Recorder())
        assertEquals("reply:payload", bufferedMessenger.requestRaw("t", "payload", timeout = null))
    }
}
