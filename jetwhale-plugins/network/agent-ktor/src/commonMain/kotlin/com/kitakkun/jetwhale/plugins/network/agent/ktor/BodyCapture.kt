package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.isPreviewableImageMediaType
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlin.io.encoding.Base64

/**
 * How much of a body travels to the host.
 *
 * @property maxBodyChars request/response bodies longer than this are truncated for transport.
 * @property maxImageBytes image bodies larger than this are skipped rather than truncated, since a
 *   partial image cannot be decoded.
 */
internal data class BodyCaptureLimits(val maxBodyChars: Int, val maxImageBytes: Int)

internal data class BodyCapture(val text: String?, val truncated: Boolean, val encoding: BodyEncoding = BodyEncoding.TEXT)

/**
 * Reads the request body for capture without breaking the actual send: channel/stream bodies
 * that can't be read without consuming them are replaced with a placeholder instead.
 */
internal fun captureRequestBodySafely(content: Any?, limits: BodyCaptureLimits): BodyCapture = when (content) {
    is OutgoingContent.ByteArrayContent -> {
        val mediaType = content.contentType?.mediaType()
        if (isPreviewableImageMediaType(mediaType)) {
            encodeImage(content.bytes(), mediaType, limits.maxImageBytes)
        } else {
            content.bytes().decodeToString().truncate(limits.maxBodyChars)
        }
    }

    is OutgoingContent -> BodyCapture(content.contentType?.let { "<$it>" }, false)

    else -> BodyCapture(null, false)
}

private fun String.truncate(max: Int): BodyCapture = if (length <= max) BodyCapture(this, false) else BodyCapture(substring(0, max), true)

/**
 * Base64-encodes an image body for transport, or replaces it with a marker when it exceeds
 * [maxImageBytes] — the host can render nothing useful from a partial image, and a large one would
 * dominate the event stream.
 */
private fun encodeImage(bytes: ByteArray, mediaType: String?, maxImageBytes: Int): BodyCapture = if (bytes.size > maxImageBytes) {
    BodyCapture("<${mediaType ?: "image"} body over the $maxImageBytes-byte maxImageBytes limit>", false)
} else {
    BodyCapture(Base64.encode(bytes), false, BodyEncoding.BASE64)
}

/** The content type without its parameters, lowercased, as [isPreviewableImageMediaType] expects. */
private fun ContentType.mediaType(): String = "$contentType/$contentSubtype".lowercase()

/**
 * Reads the response body for capture without consuming or blocking the caller's response:
 * bodies that can't be buffered safely (WebSocket upgrades, endless streams) are replaced with a
 * placeholder instead. Returns the call the caller should receive — the original one when the
 * body was left untouched, or a saved copy whose body is still readable.
 */
internal suspend fun captureResponseBodySafely(call: HttpClientCall, limits: BodyCaptureLimits): Pair<HttpClientCall, BodyCapture> {
    val response = call.response

    // A WebSocket upgrade response (101) has no conventional body — by the time this runs, the
    // connection has already switched to the raw frame stream, so save()/bodyAsText() would read
    // live WebSocket frames as if they were an HTTP body, corrupting the frame stream for the caller.
    if (response.isWebSocketUpgrade()) {
        return call to BodyCapture("<websocket upgrade>", false)
    }

    // save() buffers the entire body before returning, so on a never-ending stream (SSE) the
    // caller would wait forever for a response that has already started arriving.
    val contentType = response.headers[HttpHeaders.ContentType]
    if (contentType?.startsWith("text/event-stream", ignoreCase = true) == true) {
        return call to BodyCapture("<streaming response body>", false)
    }

    // save() buffers the body so we can read it for inspection and still hand a fresh, readable
    // response to the caller.
    val saved = call.save()
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
    if (isPreviewableImageMediaType(mediaType)) {
        return saved to encodeImage(saved.response.readRawBytes(), mediaType, limits.maxImageBytes)
    }
    return saved to saved.response.bodyAsText().truncate(limits.maxBodyChars)
}

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun HttpResponse.isWebSocketUpgrade(): Boolean = status == HttpStatusCode.SwitchingProtocols && headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true
