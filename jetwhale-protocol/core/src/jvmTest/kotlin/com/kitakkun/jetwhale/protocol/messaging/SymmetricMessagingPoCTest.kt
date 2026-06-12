package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
// standalone — `messenger.send(Pong("x"))` does not compile.
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

    private fun roundTrip(frame: PluginFrame): PluginFrame =
        wireJson.decodeFromString<PluginFrame>(wireJson.encodeToString(frame))

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    // -- fire-and-forget ------------------------------------------------------

    @Test
    fun `events arrive serially in send order`() = runBlocking {
        val received = java.util.concurrent.CopyOnWriteArrayList<Int>()
        right.configure {
            onEvent { event: CounterEvent -> received += event.value }
        }

        repeat(100) { left.messenger.send(CounterEvent(it)) }

        withTimeout(5_000) {
            while (received.size < 100) delay(10)
        }
        assertEquals((0 until 100).toList(), received.toList())
    }

    @Test
    fun `unknown event is skipped without breaking the connection`() = runBlocking {
        // right registers no CounterEvent handler at all
        right.configure {
            onRequest { ping: Ping -> Pong(ping.tag) }
        }

        left.messenger.send(CounterEvent(42)) // skipped on the right, must not break anything
        val pong: Pong = left.messenger.request(Ping("still-alive"))
        assertEquals(Pong("still-alive"), pong)
    }

    // -- request/response -----------------------------------------------------

    @Test
    fun `request infers the reply type from the request declaration`() = runBlocking {
        right.configure {
            onRequest { ping: Ping -> Pong(ping.tag.uppercase()) }
        }

        // The whole point of the marker: no type argument, no cast — `Pong` is inferred.
        val pong: Pong = left.messenger.request(Ping("hello"))
        assertEquals(Pong("HELLO"), pong)
    }

    @Test
    fun `requests work in both directions`() = runBlocking {
        left.configure {
            onRequest { _: WhoAreYou -> Identity("left") }
        }
        right.configure {
            onRequest { _: WhoAreYou -> Identity("right") }
        }

        // host->agent AND agent->host with the exact same API.
        assertEquals(Identity("right"), left.messenger.request(WhoAreYou))
        assertEquals(Identity("left"), right.messenger.request(WhoAreYou))
    }

    @Test
    fun `concurrent requests correlate to their own replies`(): Unit = runBlocking {
        right.configure {
            onRequest { ping: Ping ->
                delay(Random.nextLong(1, 30)) // shuffle completion order
                Pong(ping.tag)
            }
        }

        coroutineScope {
            val results = (0 until 50).map { i ->
                async { i to left.messenger.request(Ping("tag-$i")) }
            }.map { it.await() }

            results.forEach { (i, pong) -> assertEquals(Pong("tag-$i"), pong) }
        }
    }

    @Test
    fun `request handler may call back in the opposite direction without deadlocking`() = runBlocking {
        left.configure {
            onRequest { _: WhoAreYou -> Identity("left") }
        }
        right.configure {
            // While handling left's Ping, right requests WhoAreYou from left.
            onRequest { ping: Ping ->
                val caller: Identity = right.messenger.request(WhoAreYou)
                Pong("${ping.tag}-handled-for-${caller.name}")
            }
        }

        val pong: Pong = withTimeout(5_000) { left.messenger.request(Ping("nested")) }
        assertEquals(Pong("nested-handled-for-left"), pong)
    }

    // -- failure paths ---------------------------------------------------------

    @Test
    fun `request without a registered handler fails loudly`() = runBlocking {
        val e = assertFailsWith<JetWhaleRequestException> {
            left.messenger.execute(Ping("nobody-home"))
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
                Pong(explode.reason)
            }
        }

        val e = assertFailsWith<JetWhaleRequestException> {
            left.messenger.execute(Explode("test"))
        }
        assertTrue("boom: test" in (e.message ?: ""), "unexpected message: ${e.message}")
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
            mute.messenger.execute(Ping("lost"))
        }
        assertTrue("timed out" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test
    fun `close fails pending requests immediately`(): Unit = runBlocking {
        val silent = JetWhalePluginPeer(
            pluginId = PLUGIN_ID,
            parentScope = scope,
            sendFrame = { /* never replies */ },
        )

        coroutineScope {
            val pending = async { runCatching { silent.messenger.execute(Ping("doomed")) } }
            delay(100) // let the request register itself as pending
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
