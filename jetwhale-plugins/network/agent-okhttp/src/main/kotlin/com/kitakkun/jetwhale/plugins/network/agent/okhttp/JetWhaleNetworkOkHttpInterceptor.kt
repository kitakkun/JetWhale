package com.kitakkun.jetwhale.plugins.network.agent.okhttp

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
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
        val method = request.method
        val url = request.url.toString()

        val requestBody = captureRequestBody(request.body, maxBodyChars)
        agent.recordRequest(
            CapturedHttpRequest(
                txId = txId,
                method = method,
                url = url,
                headers = request.capturedHeaders(),
                body = requestBody.text,
                bodyTruncated = requestBody.truncated,
                timestampMs = System.currentTimeMillis(),
            ),
        )

        agent.findMock(method, url)?.let { mock ->
            if (mock.delayMs > 0) Thread.sleep(mock.delayMs)
            agent.recordResponse(
                CapturedHttpResponse(
                    txId = txId,
                    statusCode = mock.statusCode,
                    statusDescription = httpStatusDescription(mock.statusCode),
                    headers = mock.headers.mapValues { listOf(it.value) },
                    body = mock.body,
                    durationMs = started.elapsedNow().inWholeMilliseconds,
                    fromMock = true,
                ),
            )
            return buildMockResponse(request, mock)
        }

        val response = try {
            chain.proceed(request)
        } catch (cause: Throwable) {
            agent.recordFailure(
                HttpRequestFailure(
                    txId = txId,
                    message = cause.message ?: cause.toString(),
                    durationMs = started.elapsedNow().inWholeMilliseconds,
                ),
            )
            throw cause
        }

        val responseBody = captureResponseBody(response, maxBodyChars)
        agent.recordResponse(
            CapturedHttpResponse(
                txId = txId,
                statusCode = response.code,
                statusDescription = response.message,
                headers = response.headers.toCapturedMap(),
                body = responseBody.text,
                bodyTruncated = responseBody.truncated,
                durationMs = started.elapsedNow().inWholeMilliseconds,
                fromMock = false,
            ),
        )
        return response
    }
}

private data class BodyCapture(val text: String?, val truncated: Boolean)

private fun String.truncate(max: Int): BodyCapture = if (length <= max) BodyCapture(this, false) else BodyCapture(substring(0, max), true)

private fun captureRequestBody(body: RequestBody?, maxChars: Int): BodyCapture {
    if (body == null) return BodyCapture(null, false)
    // One-shot bodies can't be read twice; duplex bodies can't be materialized up front.
    if (body.isOneShot() || body.isDuplex()) return BodyCapture("<streaming request body>", false)
    return try {
        val buffer = Buffer()
        body.writeTo(buffer)
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        buffer.readString(charset).truncate(maxChars)
    } catch (e: Exception) {
        BodyCapture(null, false)
    }
}

private fun captureResponseBody(response: Response, maxChars: Int): BodyCapture {
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
        response.peekBody(maxChars + 1L).string().truncate(maxChars)
    } catch (e: Exception) {
        BodyCapture(null, false)
    }
}

private fun buildMockResponse(request: Request, mock: MockResponseSpec): Response {
    val mediaType = mock.headers.entries
        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
        ?.value
        ?.toMediaTypeOrNull()
    val builder = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(mock.statusCode)
        .message(httpStatusDescription(mock.statusCode))
        .body(mock.body.toResponseBody(mediaType))
    mock.headers.forEach { (key, value) -> builder.addHeader(key, value) }
    return builder.build()
}

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun Response.isWebSocketUpgrade(): Boolean =
    code == 101 && header("Upgrade")?.equals("websocket", ignoreCase = true) == true

/** Preserves original header-name case, unlike [Headers.toMultimap] which lowercases keys. */
private fun Headers.toCapturedMap(): Map<String, List<String>> {
    val result = LinkedHashMap<String, MutableList<String>>()
    forEach { (name, value) -> result.getOrPut(name) { mutableListOf() }.add(value) }
    return result
}

/**
 * [Request.headers] doesn't include Content-Type/Content-Length when they were derived from the
 * [RequestBody] rather than set explicitly, so backfill them from the body when absent.
 */
private fun Request.capturedHeaders(): Map<String, List<String>> {
    val result = headers.toCapturedMap().toMutableMap<String, List<String>>()
    val body = body ?: return result
    if (result.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
        body.contentType()?.let { result["Content-Type"] = listOf(it.toString()) }
    }
    if (result.keys.none { it.equals("Content-Length", ignoreCase = true) }) {
        val length = runCatching { body.contentLength() }.getOrDefault(-1L)
        if (length >= 0) result["Content-Length"] = listOf(length.toString())
    }
    return result
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
