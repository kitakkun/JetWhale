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

    // After a 101 response the connection carries the live WebSocket frames, so reading the body
    // here would take them from the caller's session.
    if (response.isWebSocketUpgrade()) {
        return call to BodyCapture("<websocket upgrade>", false)
    }

    val contentType = response.headers[HttpHeaders.ContentType]
    // save() reads the body to its end, which an SSE stream never reaches.
    if (contentType?.startsWith("text/event-stream", ignoreCase = true) == true) {
        return call to BodyCapture("<streaming response body>", false)
    }

    // Ktor's SaveBody plugin has already buffered a non-streaming body, and response.call is then
    // that saved call, which save() returns without reading the body again; call.save() would copy
    // it a second time.
    val saved = response.call.save()
    val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
    if (isPreviewableImageMediaType(mediaType)) {
        return saved to encodeImage(saved.response.readRawBytes(), mediaType, limits.maxImageBytes)
    }
    return saved to saved.response.bodyAsText().truncate(limits.maxBodyChars)
}

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun HttpResponse.isWebSocketUpgrade(): Boolean =
    status == HttpStatusCode.SwitchingProtocols && headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true
