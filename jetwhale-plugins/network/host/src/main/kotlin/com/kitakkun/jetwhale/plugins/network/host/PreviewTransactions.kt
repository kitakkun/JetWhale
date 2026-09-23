package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse

/** Captured traffic for the previews in this module: one plain JSON call and one mocked image. */
internal fun previewTransactions(): List<HttpTransaction> = listOf(
    HttpTransaction(
        request = CapturedHttpRequest(
            txId = "tx-1",
            method = "GET",
            url = "https://example.com/api/items?page=2",
            headers = mapOf("Accept" to listOf("application/json")),
            timestampMs = 0,
        ),
        response = CapturedHttpResponse(
            txId = "tx-1",
            statusCode = 200,
            statusDescription = "OK",
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = """{"items":[{"id":1,"name":"first"}]}""",
            durationMs = 128,
        ),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(
            txId = "tx-2",
            method = "POST",
            url = "https://example.com/api/items",
            timestampMs = 1,
        ),
        response = CapturedHttpResponse(
            txId = "tx-2",
            statusCode = 503,
            statusDescription = "Service Unavailable",
            durationMs = 42,
            fromMock = true,
        ),
    ),
)
