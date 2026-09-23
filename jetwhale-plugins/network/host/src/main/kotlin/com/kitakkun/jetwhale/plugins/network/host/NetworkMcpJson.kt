package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.mediaType
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
    putBody(request.body, request.bodyEncoding, request.headers)
    if (request.bodyTruncated) put("bodyTruncated", true)
}

private fun JsonObjectBuilder.putResponse(response: CapturedHttpResponse) {
    put("statusCode", response.statusCode)
    if (response.statusDescription.isNotEmpty()) put("statusDescription", response.statusDescription)
    put("durationMs", response.durationMs)
    put("fromMock", response.fromMock)
    putHeaders(response.headers)
    putBody(response.body, response.bodyEncoding, response.headers)
    if (response.bodyTruncated) put("bodyTruncated", true)
}

/**
 * A binary body is summarized rather than included: its Base64 form is megabytes of noise an agent
 * cannot use, and it would crowd out everything else in the tool result. The image itself is
 * available in the host UI, which previews and exports it.
 */
private fun JsonObjectBuilder.putBody(body: String?, encoding: BodyEncoding, headers: Map<String, List<String>>) {
    if (body == null) return
    when (encoding) {
        BodyEncoding.TEXT -> put("body", body)

        BodyEncoding.BASE64 -> {
            put("body", "<${headers.mediaType() ?: "binary"} body, ${base64DecodedSize(body)} bytes, shown in the host UI>")
            put("bodyEncoding", encoding.name)
        }
    }
}

/** Size of the bytes a padded Base64 string decodes to, without decoding it. */
internal fun base64DecodedSize(base64: String): Int = base64.length / 4 * 3 - base64.takeLast(2).count { it == '=' }

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
