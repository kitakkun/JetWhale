package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// The transaction views stay hand-assembled rather than declared as `@Serializable` result types:
// their keys are conditional (a transaction carries a response, a failure, or neither), which a
// data class would have to flatten into nullable properties that are advertised on every
// transaction and written even when they do not apply. The mock-configuration tools, whose answers
// have one fixed shape, declare theirs — see NetworkMcpResults.kt.

/** Compact one-line-per-transaction view for listTransactions. */
internal fun HttpTransaction.toSummaryJson(): JsonObject = buildJsonObject {
    put("txId", txId)
    put("method", request.method)
    put("url", request.url)
    put("timestampMs", request.timestampMs)
    response?.let {
        put("statusCode", it.statusCode)
        put("durationMs", it.durationMs)
        put("fromMock", it.fromMock)
    }
    failure?.let {
        put("failed", true)
        put("failureMessage", it.message)
    }
    if (response == null && failure == null) put("pending", true)
}

/** Full transaction view for getTransaction, including headers and bodies. */
internal fun HttpTransaction.toDetailJson(): JsonObject = buildJsonObject {
    put("txId", txId)
    putJsonObject("request") { putRequest(request) }
    response?.let { putJsonObject("response") { putResponse(it) } }
    failure?.let { putJsonObject("failure") { putFailure(it) } }
}

private fun JsonObjectBuilder.putRequest(request: CapturedHttpRequest) {
    put("method", request.method)
    put("url", request.url)
    put("timestampMs", request.timestampMs)
    putHeaders(request.headers)
    request.body?.let { put("body", it) }
    if (request.bodyTruncated) put("bodyTruncated", true)
}

private fun JsonObjectBuilder.putResponse(response: CapturedHttpResponse) {
    put("statusCode", response.statusCode)
    if (response.statusDescription.isNotEmpty()) put("statusDescription", response.statusDescription)
    put("durationMs", response.durationMs)
    put("fromMock", response.fromMock)
    putHeaders(response.headers)
    response.body?.let { put("body", it) }
    if (response.bodyTruncated) put("bodyTruncated", true)
}

private fun JsonObjectBuilder.putFailure(failure: HttpRequestFailure) {
    put("message", failure.message)
    put("durationMs", failure.durationMs)
}

private fun JsonObjectBuilder.putHeaders(headers: Map<String, List<String>>) {
    putJsonObject("headers") {
        headers.forEach { (name, values) ->
            putJsonArray(name) { values.forEach { add(it) } }
        }
    }
}
