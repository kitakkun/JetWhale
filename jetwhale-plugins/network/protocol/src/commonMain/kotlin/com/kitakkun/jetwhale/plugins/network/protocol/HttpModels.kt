package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.Serializable

/** How the bytes of a captured body are carried in the `body` string. */
@Serializable
enum class BodyEncoding {
    /** The body is the decoded text itself. */
    TEXT,

    /** The body is Base64-encoded bytes, used for binary payloads the host renders rather than reads. */
    BASE64,
}

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
    val bodyEncoding: BodyEncoding = BodyEncoding.TEXT,
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
    val bodyEncoding: BodyEncoding = BodyEncoding.TEXT,
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

/** The `Content-Type` media type of these headers, without parameters and lowercased. */
fun Map<String, List<String>>.mediaType(): String? = entries
    .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
    ?.value
    ?.firstOrNull()
    ?.substringBefore(';')
    ?.trim()
    ?.lowercase()

/**
 * True for the image formats captured as bytes so the host can render a preview.
 *
 * SVG is excluded on purpose: it is markup, more useful read as text than decoded as a bitmap.
 */
fun isPreviewableImageMediaType(mediaType: String?): Boolean = mediaType != null && mediaType.startsWith("image/") && mediaType != "image/svg+xml"
