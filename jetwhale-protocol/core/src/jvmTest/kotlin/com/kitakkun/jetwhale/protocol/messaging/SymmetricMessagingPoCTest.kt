package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// ---------------------------------------------------------------------------
// PoC message set: plain @Serializable classes, roles declared by markers only.
// The reply types (Pong, Identity) implement NO marker: they cannot be sent
// standalone — `messenger.trySend(Pong("x"))` does not compile.
// ---------------------------------------------------------------------------

@Serializable
@SerialName("poc/counter")
private data class CounterEvent(val value: Int) : JetWhaleEvent

@Serializable
@SerialName("poc/ping")
private data class Ping(val tag: String) : JetWhaleRequest<Pong>

@Serializable
@SerialName("poc/pong")
private data class Pong(val tag: String)

@Serializable
@SerialName("poc/who-are-you")
private data object WhoAreYou : JetWhaleRequest<Identity>

@Serializable
@SerialName("poc/identity")
private data class Identity(val name: String)

@Serializable
@SerialName("poc/explode")
private data class Explode(val reason: String) : JetWhaleRequest<Pong>

private const val PLUGIN_ID = "com.kitakkun.jetwhale.poc"

class SymmetricMessagingPoCTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wireJson = Json

    /**
     * Two peers connected back-to-back. Every frame is serialized to a JSON string and parsed
     * back before delivery, so each test also proves the frames survive the actual wire format.
     */
    private var left: JetWhalePluginPeer
    private var right: JetWhalePluginPeer

    init {
        left = JetWhalePluginPeer(PLUGIN_ID, scope, sendFrame = { right.onFrame(roundTrip(it)) })
        right = JetWhalePluginPeer(PLUGIN_ID, scope, sendFrame = { left.onFrame(roundTrip(it)) })
    }

    private fun roundTrip(frame: PluginFrame): PluginFrame = wireJson.decodeFromString<PluginFrame>(wireJson.encodeToString(frame))

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    // -- prepare barrier -------------------------------------------------------

    @Test
    fun `an awaitReady peer holds handler dispatch until markReady, but its own requests complete`() = runBlocking {
        val received = Channel<Int>(Channel.UNLIMITED)
        lateinit var preparing: JetWhalePluginPeer
        val remote = JetWhalePluginPeer(PLUGIN_ID, scope, sendFrame = { preparing.onFrame(roundTrip(it)) })
        preparing = JetWhalePluginPeer(
            pluginId = PLUGIN_ID,
            parentScope = scope,
            sendFrame = { remote.onFrame(roundTrip(it)) },
            awaitReady = true,
        )
        remote.configure {
            onRequest { ping: Ping -> reply(Pong(ping.tag)) }
        }
        preparing.configure {
            onEvent { event: CounterEvent -> received.send(event.value) }
        }

        // The remote sends events before the preparing side is ready: they must be held.
        remote.messenger.trySend(CounterEvent(1))
        remote.messenger.trySend(CounterEvent(2))
        // Outbound requests from the preparing side (what onPrepare does) still complete: replies
        // bypass the gate. Their round trip also gives any premature dispatch time to show up.
        assertEquals(Pong("during-prepare"), preparing.messenger.request(Ping("during-prepare")))
        assertTrue(received.tryReceive().isFailure, "events must not be dispatched before markReady")

        preparing.markReady()
        val flushed = withTimeout(5_000) { listOf(received.receive(), received.receive()) }
        assertEquals(listOf(1, 2), flushed, "held events flush in arrival order")
    }

    // -- fire-and-forget ------------------------------------------------------

    @Test
    fun `events arrive serially in send order`() = runBlocking {
        val received = Channel<Int>(Channel.UNLIMITED)
        right.configure {
            onEvent { event: CounterEvent -> received.send(event.value) }
        }

        repeat(100) { left.messenger.trySend(CounterEvent(it)) }

        val arrived = withTimeout(5_000) { List(100) { received.receive() } }
        assertEquals((0 until 100).toList(), arrived)
    }

    @Test
    fun `unknown event is skipped without breaking the connection`() = runBlocking {
        // right registers no CounterEvent handler at all
        right.configure {
            onRequest { ping: Ping -> reply(Pong(ping.tag)) }
        }

        left.messenger.trySend(CounterEvent(42)) // skipped on the right, must not break anything
        val pong: Pong = left.messenger.request(Ping("still-alive"))
        assertEquals(Pong("still-alive"), pong)
    }

    @Test
    fun `a request sent after a notification is dispatched after that notification is handled`() = runBlocking {
        val order = CopyOnWriteArrayList<String>()
        val requestQueued = CompletableDeferred<Unit>()
        lateinit var responder: JetWhalePluginPeer
        val caller = JetWhalePluginPeer(
            pluginId = PLUGIN_ID,
            parentScope = scope,
            sendFrame = { frame ->
                responder.onFrame(roundTrip(frame))
                if (frame is PluginFrame.Request) requestQueued.complete(Unit)
            },
        )
        responder = JetWhalePluginPeer(PLUGIN_ID, scope, sendFrame = { caller.onFrame(roundTrip(it)) })
        responder.configure {
            // The notification handler is held until the request is on the same inbound queue, so a
            // dispatch that ignored arrival order would run the request while this one still waits.
            onEvent { _: CounterEvent ->
                requestQueued.await()
                order += "event"
            }
            onRequest { ping: Ping ->
                order += "request"
                reply(Pong(ping.tag))
            }
        }

        caller.messenger.trySend(CounterEvent(1))
        val pong: Pong = withTimeout(5_000) { caller.messenger.request(Ping("after-event")) }

        assertEquals(Pong("after-event"), pong)
        assertEquals(listOf("event", "request"), order.toList())
    }

    // -- request/response -----------------------------------------------------

    @Test
    fun `request infers the reply type from the request declaration`() = runBlocking {
        right.configure {
            onRequest { ping: Ping -> reply(Pong(ping.tag.uppercase())) }
        }

        // The whole point of the marker: no type argument, no cast — `Pong` is inferred.
        val pong: Pong = left.messenger.request(Ping("hello"))
        assertEquals(Pong("HELLO"), pong)
    }

    @Test
    fun `requests work in both directions`() = runBlocking {
        left.configure {
            onRequest { _: WhoAreYou -> reply(Identity("left")) }
        }
        right.configure {
            onRequest { _: WhoAreYou -> reply(Identity("right")) }
        }

        // host->agent AND agent->host with the exact same API.
        assertEquals(Identity("right"), left.messenger.request(WhoAreYou))
        assertEquals(Identity("left"), right.messenger.request(WhoAreYou))
    }

    @Test
    fun `concurrent requests correlate to their own replies`(): Unit = runBlocking {
        val tags = (0 until 50).map { "tag-$it" }
        val arrived = Channel<String>(Channel.UNLIMITED)
        val replyGates = tags.associateWith { CompletableDeferred<Unit>() }
        right.configure {
            onRequest { ping: Ping ->
                arrived.send(ping.tag)
                replyGates.getValue(ping.tag).await()
                reply(Pong(ping.tag))
            }
        }

        coroutineScope {
            val pending = tags.map { tag -> tag to async { left.messenger.request<Ping, Pong>(Ping(tag)) } }

            withTimeout(5_000) { repeat(tags.size) { arrived.receive() } }
            // Replies are completed in an order unrelated to the send order, so only correlation —
            // not arrival order — can match each reply to its own request.
            tags.shuffled(Random(seed = 1)).forEach { replyGates.getValue(it).complete(Unit) }

            pending.forEach { (tag, pong) -> assertEquals(Pong(tag), pong.await()) }
        }
    }

    @Test
    fun `request handler may call back in the opposite direction without deadlocking`() = runBlocking {
        left.configure {
            onRequest { _: WhoAreYou -> reply(Identity("left")) }
        }
        right.configure {
            // While handling left's Ping, right requests WhoAreYou from left.
            onRequest { ping: Ping ->
                val caller: Identity = right.messenger.request(WhoAreYou)
                reply(Pong("${ping.tag}-handled-for-${caller.name}"))
            }
        }

        val pong: Pong = withTimeout(5_000) { left.messenger.request(Ping("nested")) }
        assertEquals(Pong("nested-handled-for-left"), pong)
    }

    @Test
    fun `inbound requests beyond the concurrency bound are rejected fast`(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>() // holds every handler in-flight until released

        // A responder bounded to `maxConcurrent` in-flight requests, wired back-to-back to a caller.
        lateinit var responder: JetWhalePluginPeer
        val caller = JetWhalePluginPeer(PLUGIN_ID, scope, sendFrame = { responder.onFrame(roundTrip(it)) })
        val maxConcurrent = 3
        responder = JetWhalePluginPeer(
            pluginId = PLUGIN_ID,
            parentScope = scope,
            sendFrame = { caller.onFrame(roundTrip(it)) },
            maxConcurrentRequests = maxConcurrent,
        )
        responder.configure {
            onRequest { ping: Ping ->
                gate.await()
                reply(Pong(ping.tag))
            }
        }

        val total = 8
        coroutineScope {
            val settled = Channel<Result<Pong>>(Channel.UNLIMITED)
            val pending = (0 until total).map { i ->
                // Capture the messaging outcome without runCatching, which would also swallow cancellation.
                async { messagingResult { caller.messenger.request<Ping, Pong>(Ping("t$i")) }.also { settled.send(it) } }
            }
            // A request is only rejected once the bound is reached, so these first outcomes landing
            // means every request has reached the responder and taken or been refused a slot. The
            // ones that did get a slot cannot settle yet: their handler is holding on the gate.
            withTimeout(5_000) { repeat(total - maxConcurrent) { settled.receive() } }
            gate.complete(Unit) // release the handlers that did get a slot
            val outcomes = pending.map { it.await() }

            val succeeded = outcomes.count { it.isSuccess }
            val rejected = outcomes.count {
                (it.exceptionOrNull() as? JetWhaleRequestException)?.message?.contains("Too many concurrent requests") == true
            }
            assertEquals(maxConcurrent, succeeded, "only the bounded number of handlers should run")
            assertEquals(total - maxConcurrent, rejected, "the rest should be rejected fast")
        }
    }

    // -- failure paths ---------------------------------------------------------

    @Test
    fun `request without a registered handler fails loudly`() = runBlocking {
        val e = assertFailsWith<JetWhaleRequestException> {
            left.messenger.request(Ping("nobody-home"))
        }
        assertTrue("No request handler registered" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test
    fun `handler exception surfaces as a request failure with the message`() = runBlocking {
        right.configure {
            // A realistic failing handler validates then throws; the reply-typed return path
            // (Pong) keeps R pinned even though this input always throws.
            onRequest { explode: Explode ->
                require(explode.reason != "test") { "boom: ${explode.reason}" }
                reply(Pong(explode.reason))
            }
        }

        val e = assertFailsWith<JetWhaleRequestException> {
            left.messenger.request(Explode("test"))
        }
        assertTrue("boom: test" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test
    fun `a per-call timeout overrides the peer default`() = runBlocking {
        val neverReplies = CompletableDeferred<Unit>()
        right.configure {
            onRequest { ping: Ping ->
                neverReplies.await()
                reply(Pong(ping.tag))
            }
        }

        // The handler never replies, so the request can only end in a timeout. Failing well inside
        // the peer's 5s default is what proves the 200ms per-call value is the one that was applied.
        val e = withTimeout(2_000) {
            assertFailsWith<JetWhaleRequestException> {
                left.messenger.request<Ping, Pong>(Ping("slow"), timeout = 200.milliseconds)
            }
        }
        assertTrue("timed out" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test
    fun `request times out when the transport drops frames`() = runBlocking {
        val mute = JetWhalePluginPeer(
            pluginId = PLUGIN_ID,
            parentScope = scope,
            sendFrame = { /* dropped */ },
            requestTimeout = 200.milliseconds,
        )

        val e = assertFailsWith<JetWhaleRequestException> {
            mute.messenger.request(Ping("lost"))
        }
        assertTrue("timed out" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test
    fun `close fails pending requests immediately`(): Unit = runBlocking {
        val requestOnTheWire = CompletableDeferred<Unit>()
        val silent = JetWhalePluginPeer(
            pluginId = PLUGIN_ID,
            parentScope = scope,
            sendFrame = { requestOnTheWire.complete(Unit) }, // never replies
        )

        coroutineScope {
            val pending = async { messagingResult { silent.messenger.request<Ping, Pong>(Ping("doomed")) } }
            // The peer registers a request as pending before handing the frame to the transport, so
            // by the time the frame is on the wire there is a pending request for close() to fail.
            withTimeout(5_000) { requestOnTheWire.await() }
            silent.close()
            val result = pending.await()
            assertTrue(result.exceptionOrNull() is JetWhaleConnectionClosedException, "got: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun `duplicate registration is rejected at configure time`() {
        assertFailsWith<IllegalStateException> {
            left.configure {
                onEvent { _: CounterEvent -> }
                onEvent { _: CounterEvent -> }
            }
        }
    }
}

/**
 * Like `runCatching`, but only captures [JetWhaleMessagingException] — so a [CancellationException]
 * keeps propagating instead of being swallowed (which would break structured concurrency).
 */
private inline fun <T> messagingResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: JetWhaleMessagingException) {
    Result.failure(e)
}
