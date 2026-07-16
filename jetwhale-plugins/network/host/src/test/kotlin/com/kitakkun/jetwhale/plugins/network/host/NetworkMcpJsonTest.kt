package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkMcpJsonTest {
    private val request = CapturedHttpRequest(
        txId = "tx-1",
        method = "GET",
        url = "https://example.com/users",
        headers = mapOf("Accept" to listOf("application/json")),
        body = null,
        timestampMs = 1000,
    )

    @Test
    fun `summary of a pending transaction is flagged pending`() {
        val json = HttpTransaction(request = request).toSummaryJson()
        assertEquals("tx-1", json["txId"]?.jsonPrimitive?.content)
        assertEquals(true, json["pending"]?.jsonPrimitive?.boolean)
        assertNull(json["statusCode"])
    }

    @Test
    fun `summary of a completed transaction carries status and duration`() {
        val response = CapturedHttpResponse(txId = "tx-1", statusCode = 200, durationMs = 42, fromMock = true)
        val json = HttpTransaction(request = request, response = response).toSummaryJson()
        assertEquals(200, json["statusCode"]?.jsonPrimitive?.int)
        assertEquals(42, json["durationMs"]?.jsonPrimitive?.int)
        assertEquals(true, json["fromMock"]?.jsonPrimitive?.boolean)
        assertNull(json["pending"])
    }

    @Test
    fun `summary of a failed transaction carries the failure message`() {
        val failure = HttpRequestFailure(txId = "tx-1", message = "connection reset", durationMs = 5)
        val json = HttpTransaction(request = request, failure = failure).toSummaryJson()
        assertEquals(true, json["failed"]?.jsonPrimitive?.boolean)
        assertEquals("connection reset", json["failureMessage"]?.jsonPrimitive?.content)
    }

    @Test
    fun `detail includes request headers and response body`() {
        val response = CapturedHttpResponse(
            txId = "tx-1",
            statusCode = 200,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = """{"ok":true}""",
            durationMs = 42,
        )
        val json = HttpTransaction(request = request, response = response).toDetailJson()
        val requestJson = json["request"]!!.jsonObject
        assertEquals(
            "application/json",
            requestJson["headers"]!!.jsonObject["Accept"]!!.jsonArray[0].jsonPrimitive.content,
        )
        val responseJson = json["response"]!!.jsonObject
        assertEquals("""{"ok":true}""", responseJson["body"]?.jsonPrimitive?.content)
        assertNull(json["failure"])
    }
}
