package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class NetworkConditionKtorTest {
    private val agentAndEvents = agentWithEvents()
    private val agent = agentAndEvents.first
    private val events = agentAndEvents.second
    private var engineCalls = 0
    private var uploadedBytes = 0

    private val client = HttpClient(
        MockEngine { request ->
            engineCalls++
            uploadedBytes = request.body.toByteArray().size
            respond(ByteArray(PACED_BODY_BYTES))
        },
    ) { install(agent.ktorClientPlugin()) }

    @Test
    fun `an offline condition fails at once without reaching the engine`() = runBlocking {
        agent.applyNetworkConditions(listOf(conditionRule(NetworkCondition(offline = true), matcher = null)))

        assertFailsWith<IOException> { client.get("https://example.com/items") }

        assertEquals(0, engineCalls)
        assertEquals(true, events.filterIsInstance<RequestFailed>().single().failure.condition?.offline)
    }

    @Test
    fun `an injected timeout surfaces as the socket timeout Ktor throws`() = runBlocking {
        agent.applyNetworkConditions(listOf(conditionRule(NetworkCondition(failureRate = 1.0, failure = InjectedFailure.TIMEOUT), matcher = null)))

        assertFailsWith<SocketTimeoutException> { client.get("https://example.com/items") }

        assertEquals(0, engineCalls)
    }

    @Test
    fun `a download cap paces the body as the app reads it`() = runBlocking {
        agent.applyNetworkConditions(listOf(conditionRule(NetworkCondition(downloadBytesPerSecond = PACED_RATE), matcher = null)))

        // Timed from the moment the app holds the response, so a body buffered during the send and
        // then handed over at once would read in no time and fail this.
        val (bytes, readMs) = client.prepareGet("https://example.com/large").execute { response ->
            val reading = TimeSource.Monotonic.markNow()
            response.readRawBytes() to reading.elapsedNow().inWholeMilliseconds
        }

        assertEquals(PACED_BODY_BYTES, bytes.size)
        assertTrue(readMs >= PACED_MIN_MS, "the app read the body in ${readMs}ms")
        assertEquals(PACED_RATE, events.filterIsInstance<ResponseReceived>().single().response.condition?.downloadBytesPerSecond)
    }

    @Test
    fun `an upload cap paces the request body`() = runBlocking {
        agent.applyNetworkConditions(listOf(conditionRule(NetworkCondition(uploadBytesPerSecond = PACED_RATE), matcher = null)))

        val started = TimeSource.Monotonic.markNow()
        client.post("https://example.com/upload") { setBody(ByteArray(PACED_BODY_BYTES)) }
        val elapsed = started.elapsedNow().inWholeMilliseconds

        assertEquals(PACED_BODY_BYTES, uploadedBytes)
        assertTrue(elapsed >= PACED_MIN_MS, "upload took ${elapsed}ms")
    }

    @Test
    fun `a rule for one endpoint leaves the others alone`() = runBlocking {
        agent.applyNetworkConditions(listOf(conditionRule(NetworkCondition(offline = true), matcher = MockMatcher(urlPattern = "/images/"))))

        client.get("https://example.com/items")

        assertNull(events.filterIsInstance<ResponseReceived>().single().response.condition)
        assertFailsWith<IOException> { client.get("https://example.com/images/a.png") }
        Unit
    }

    @OptIn(InternalJetWhaleApi::class)
    @Test
    fun `conditions are dropped when the plugin is deactivated`() = runBlocking {
        agent.applyNetworkConditions(listOf(conditionRule(NetworkCondition(offline = true), matcher = null)))

        agent.dispatchDeactivate()
        client.get("https://example.com/items")

        assertEquals(1, engineCalls)
    }

    private fun conditionRule(condition: NetworkCondition, matcher: MockMatcher?) = NetworkConditionRule(id = "rule", name = "Test network", enabled = true, matcher = matcher, condition = condition)

    private companion object {
        /** Paced at [PACED_RATE], this body takes 600 ms; the assertions allow for timer slack. */
        const val PACED_BODY_BYTES = 60_000
        const val PACED_RATE = 100_000L
        const val PACED_MIN_MS = 500L
    }
}
