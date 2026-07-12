package com.kitakkun.jetwhale.plugins.network.agent.okhttp

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer
import kotlin.time.TimeSource

/**
 * OkHttp [Interceptor] that feeds request/response details to a [JetWhaleNetworkAgentPlugin] and
 * serves mock responses configured from the host.
 *
 * Install it as an *application* interceptor (not a network interceptor) so mock responses can be
 * served without ever touching the network, and add it after your other application interceptors
 * (e.g. auth) so their added headers are captured too:
 * ```
 * val agent = JetWhaleNetworkAgentPlugin()
 * val client = OkHttpClient.Builder().addInterceptor(agent.okHttpInterceptor()).build()
 * startJetWhale { plugins { register(agent) } }
 * ```
 *
 * Engine-added headers (`Accept-Encoding`, `Host`, `User-Agent`, ...) and intermediate redirect
 * hops are not visible to an application interceptor and are omitted; only the final response is
 * captured.
 *
 * Known limitation: response bodies are captured by peeking up to [maxBodyChars] before the
 * response is returned, so a long-lived streaming response that isn't `text/event-stream` (which
 * is skipped) delays the caller until that many bytes have arrived or the stream ends.
 *
 * @param maxBodyChars request/response bodies longer than this are truncated for transport.
 */
fun JetWhaleNetworkAgentPlugin.okHttpInterceptor(maxBodyChars: Int = 100_000): Interceptor = JetWhaleNetworkOkHttpInterceptor(agent = this, maxBodyChars = maxBodyChars)

private class JetWhaleNetworkOkHttpInterceptor(
    private val agent: JetWhaleNetworkAgentPlugin,
    private val maxBodyChars: Int,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val txId = agent.newTransactionId()
        val started = TimeSource.Monotonic.markNow()

        recordRequest(request, txId)
        val mock = agent.findMock(request.method, request.url.toString())

        val response = if (mock != null) {
            serveMock(request, mock)
        } else {
            sendRequest(chain, request, txId, started)
        }
        recordResponse(response, fromMock = mock != null, txId = txId, started = started)
        return response
    }

    private fun recordRequest(request: Request, txId: String) {
        val body = captureRequestBodySafely(request.body, maxBodyChars)
        agent.recordRequest(
            CapturedHttpRequest(
                txId = txId,
                method = request.method,
                url = request.url.toString(),
                headers = request.headersWithBodyDefaults(),
                body = body.text,
                bodyTruncated = body.truncated,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun serveMock(request: Request, mock: MockResponseSpec): Response {
        if (mock.delayMs > 0) Thread.sleep(mock.delayMs)
        return buildMockResponse(request, mock)
    }

    private fun sendRequest(chain: Interceptor.Chain, request: Request, txId: String, started: TimeSource.Monotonic.ValueTimeMark): Response = try {
        chain.proceed(request)
    } catch (e: Throwable) {
        agent.recordFailure(
            HttpRequestFailure(
                txId = txId,
                message = e.message ?: e.toString(),
                durationMs = started.elapsedNow().inWholeMilliseconds,
            ),
        )
        throw e
    }

    private fun recordResponse(response: Response, fromMock: Boolean, txId: String, started: TimeSource.Monotonic.ValueTimeMark) {
        val body = captureResponseBodySafely(response, maxBodyChars)
        agent.recordResponse(
            CapturedHttpResponse(
                txId = txId,
                statusCode = response.code,
                statusDescription = response.message,
                headers = response.headers.toCapturedMap(),
                body = body.text,
                bodyTruncated = body.truncated,
                durationMs = started.elapsedNow().inWholeMilliseconds,
                fromMock = fromMock,
            ),
        )
    }
}

private data class BodyCapture(val text: String?, val truncated: Boolean)

private fun String.truncate(max: Int): BodyCapture = if (length <= max) BodyCapture(this, false) else BodyCapture(substring(0, max), true)

/**
 * Reads the request body for capture without breaking the actual send: bodies that can't be
 * materialized up front (one-shot, duplex) are replaced with a placeholder, and reads are
 * byte-capped so large uploads aren't held in memory.
 */
private fun captureRequestBodySafely(body: RequestBody?, maxChars: Int): BodyCapture {
    if (body == null) return BodyCapture(null, false)
    // One-shot bodies can't be read twice; duplex bodies can't be materialized up front.
    if (body.isOneShot() || body.isDuplex()) return BodyCapture("<streaming request body>", false)
    return try {
        // Keep at most maxChars * 4 bytes (the widest UTF encoding of one char) so large uploads
        // (files, multipart) are streamed through instead of fully materialized in memory.
        val sink = TruncatingSink(maxBytes = maxChars * 4L)
        sink.buffer().use { body.writeTo(it) }
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val capture = sink.captured.readString(charset).truncate(maxChars)
        if (sink.overflowed && !capture.truncated) capture.copy(truncated = true) else capture
    } catch (_: Exception) {
        BodyCapture(null, false)
    }
}

/** Retains the first [maxBytes] written and discards the rest, flagging [overflowed]. */
private class TruncatingSink(private val maxBytes: Long) : Sink {
    val captured = Buffer()
    var overflowed = false
        private set

    override fun write(source: Buffer, byteCount: Long) {
        val keep = minOf(byteCount, maxBytes - captured.size)
        if (keep > 0) captured.write(source, keep)
        if (byteCount > keep) {
            overflowed = true
            source.skip(byteCount - keep)
        }
    }

    override fun flush() {}
    override fun timeout(): Timeout = Timeout.NONE
    override fun close() {}
}

/**
 * Reads the response body for capture without consuming or blocking the caller's response:
 * bodies that can't be peeked safely (WebSocket upgrades, encoded bodies, endless streams)
 * are replaced with a placeholder instead.
 */
private fun captureResponseBodySafely(response: Response, maxChars: Int): BodyCapture {
    if (response.isWebSocketUpgrade()) {
        // A WebSocket upgrade response (101) has no conventional body — the connection has
        // already switched to the raw frame stream by the time this interceptor sees it, so
        // peekBody would read live WebSocket frames as if they were an HTTP body, corrupting the
        // frame stream for the caller.
        return BodyCapture("<websocket upgrade>", false)
    }
    val encoding = response.header("Content-Encoding")
    if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
        // Only present at this layer when the caller set Accept-Encoding itself, disabling
        // OkHttp's transparent gzip handling — peekBody would return non-text bytes here.
        return BodyCapture("<Content-Encoding: $encoding body>", false)
    }
    val contentType = response.header("Content-Type")
    if (contentType?.startsWith("text/event-stream", ignoreCase = true) == true) {
        // peekBody(n) blocks until n bytes are buffered or EOF — would hang on a never-ending stream.
        return BodyCapture("<streaming response body>", false)
    }
    return try {
        val peeked = response.peekBody(maxChars + 1L)
        val capture = peeked.string().truncate(maxChars)
        // The peek limit is in bytes but truncate() counts chars, so a multi-byte body can hit the
        // byte cap while still decoding to fewer than maxChars chars — flag it truncated anyway.
        if (peeked.contentLength() > maxChars && !capture.truncated) capture.copy(truncated = true) else capture
    } catch (_: Exception) {
        BodyCapture(null, false)
    }
}

private fun buildMockResponse(request: Request, mock: MockResponseSpec): Response {
    val headers = mock.headers.toHeaders()
    return Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(mock.statusCode)
        .message(httpStatusDescription(mock.statusCode))
        .headers(headers)
        .body(mock.body.toResponseBody(headers["Content-Type"]?.toMediaTypeOrNull()))
        .build()
}

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun Response.isWebSocketUpgrade(): Boolean =
    code == 101 && header("Upgrade")?.equals("websocket", ignoreCase = true) == true

/** Preserves original header-name case, unlike [Headers.toMultimap] which lowercases keys. */
private fun Headers.toCapturedMap(): Map<String, List<String>> = groupBy({ it.first }, { it.second })

/**
 * [Request.headers] doesn't include Content-Type/Content-Length when they were derived from the
 * [RequestBody] rather than set explicitly, so backfill them from the body when absent.
 */
private fun Request.headersWithBodyDefaults(): Map<String, List<String>> = buildMap {
    putAll(headers.toCapturedMap())
    val body = body ?: return@buildMap
    if (keys.none { it.equals("Content-Type", ignoreCase = true) }) {
        body.contentType()?.let { put("Content-Type", listOf(it.toString())) }
    }
    if (keys.none { it.equals("Content-Length", ignoreCase = true) }) {
        val length = runCatching { body.contentLength() }.getOrDefault(-1L)
        if (length >= 0) put("Content-Length", listOf(length.toString()))
    }
}

private fun httpStatusDescription(code: Int): String = when (code) {
    200 -> "OK"
    201 -> "Created"
    204 -> "No Content"
    301 -> "Moved Permanently"
    302 -> "Found"
    304 -> "Not Modified"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    409 -> "Conflict"
    429 -> "Too Many Requests"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Timeout"
    else -> ""
}
