package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.Serializable

/**
 * Transport-agnostic representation of an outgoing HTTP request captured by an adapter
 * (Ktor, OkHttp).
 */
@Serializable
data class CapturedHttpRequest(
    val txId: String,
    val method: String,
    val url: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
    val bodyTruncated: Boolean = false,
    val timestampMs: Long,
)

/**
 * Transport-agnostic representation of an HTTP response captured for a request [txId].
 */
@Serializable
data class CapturedHttpResponse(
    val txId: String,
    val statusCode: Int,
    val statusDescription: String = "",
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
    val bodyTruncated: Boolean = false,
    val durationMs: Long,
    val fromMock: Boolean = false,
)

/**
 * A request that failed before a response was produced (network error, cancellation, ...).
 */
@Serializable
data class HttpRequestFailure(
    val txId: String,
    val message: String,
    val durationMs: Long,
)
