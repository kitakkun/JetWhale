package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.StringValues
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Ktor [io.ktor.client.HttpClient] plugin that feeds request/response details to a
 * [JetWhaleNetworkAgentPlugin] and serves mock responses configured from the host.
 *
 * Install it in your client and register the same agent plugin with JetWhale:
 * ```
 * val agent = JetWhaleNetworkAgentPlugin()
 * val client = HttpClient { install(agent.ktorClientPlugin()) }
 * startJetWhale { plugins { register(agent) } }
 * ```
 *
 * Known limitation: response bodies are buffered with `save()` before the caller sees them, so a
 * long-lived streaming response that isn't `text/event-stream` (which is skipped) is fully
 * buffered and delays the caller until the stream ends.
 *
 * @param maxBodyChars request/response bodies longer than this are truncated for transport.
 */
fun JetWhaleNetworkAgentPlugin.ktorClientPlugin(maxBodyChars: Int = 100_000): ClientPlugin<Unit> {
    val agent = this
    return createClientPlugin("JetWhaleNetworkMonitor") {
        on(Send) { request ->
            val txId = agent.newTransactionId()
            val started = TimeSource.Monotonic.markNow()
            val method = request.method.value
            val url = request.url.buildString()

            agent.recordRequest(request, txId, method, url, maxBodyChars)
            val mock = agent.findMock(method, url)

            val call = if (mock != null) {
                serveMock(client, request, mock)
            } else {
                try {
                    proceed(request)
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
            }

            val (readableCall, body) = captureResponseBodySafely(call, maxBodyChars)
            agent.recordResponse(
                CapturedHttpResponse(
                    txId = txId,
                    statusCode = readableCall.response.status.value,
                    statusDescription = readableCall.response.status.description,
                    headers = readableCall.response.headers.toCapturedMap(),
                    body = body.text,
                    bodyTruncated = body.truncated,
                    durationMs = started.elapsedNow().inWholeMilliseconds,
                    fromMock = mock != null,
                ),
            )
            readableCall
        }
    }
}

private fun JetWhaleNetworkAgentPlugin.recordRequest(request: HttpRequestBuilder, txId: String, method: String, url: String, maxBodyChars: Int) {
    val body = captureRequestBodySafely(request.body, maxBodyChars)
    recordRequest(
        CapturedHttpRequest(
            txId = txId,
            method = method,
            url = url,
            headers = request.capturedRequestHeaders(),
            body = body.text,
            bodyTruncated = body.truncated,
            timestampMs = GMTDate().timestamp,
        ),
    )
}

@OptIn(InternalAPI::class) // HttpClientCall's constructor is needed to synthesize mock responses.
private suspend fun serveMock(client: HttpClient, request: HttpRequestBuilder, mock: MockResponseSpec): HttpClientCall {
    if (mock.delayMs > 0) delay(mock.delayMs.milliseconds)
    val responseData = HttpResponseData(
        statusCode = HttpStatusCode.fromValue(mock.statusCode),
        requestTime = GMTDate(),
        headers = HeadersBuilder().apply {
            mock.headers.forEach { (key, value) -> append(key, value) }
        }.build(),
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(mock.body.encodeToByteArray()),
        callContext = currentCoroutineContext(),
    )
    return HttpClientCall(client, request.build(), responseData)
}

private data class BodyCapture(val text: String?, val truncated: Boolean)

private fun String.truncate(max: Int): BodyCapture = if (length <= max) BodyCapture(this, false) else BodyCapture(substring(0, max), true)

/**
 * Reads the request body for capture without breaking the actual send: channel/stream bodies
 * that can't be read without consuming them are replaced with a placeholder instead.
 */
private fun captureRequestBodySafely(content: Any?, maxChars: Int): BodyCapture = when (content) {
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString().truncate(maxChars)
    is OutgoingContent -> BodyCapture(content.contentType?.let { "<$it>" }, false)
    else -> BodyCapture(null, false)
}

/**
 * Reads the response body for capture without consuming or blocking the caller's response:
 * bodies that can't be buffered safely (WebSocket upgrades, endless streams) are replaced with a
 * placeholder instead. Returns the call the caller should receive — the original one when the
 * body was left untouched, or a saved copy whose body is still readable.
 */
private suspend fun captureResponseBodySafely(call: HttpClientCall, maxChars: Int): Pair<HttpClientCall, BodyCapture> {
    val response = call.response

    // A WebSocket upgrade response (101) has no conventional body — by the time this plugin sees
    // it, the connection has already switched to the raw frame stream, so save()/bodyAsText()
    // would read live WebSocket frames as if they were an HTTP body, corrupting the frame stream
    // for the caller.
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
    return saved to saved.response.bodyAsText().truncate(maxChars)
}

private fun StringValues.toCapturedMap(): Map<String, List<String>> = entries().associate { it.key to it.value }

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun HttpResponse.isWebSocketUpgrade(): Boolean = status == HttpStatusCode.SwitchingProtocols && headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true

/**
 * Captures the request headers visible at the [Send] phase, enriched with the body's
 * Content-Type / Content-Length and any body-level headers. Headers injected later by the engine
 * (e.g. User-Agent, Accept-Encoding, Host) are not visible to a client plugin and are omitted.
 */
private fun HttpRequestBuilder.capturedRequestHeaders(): Map<String, List<String>> = buildMap {
    putAll(headers.build().toCapturedMap())
    val content = body as? OutgoingContent ?: return@buildMap
    content.contentType?.let { type ->
        if (!containsKey("Content-Type")) put("Content-Type", listOf(type.toString()))
    }
    content.contentLength?.let { length ->
        if (!containsKey("Content-Length")) put("Content-Length", listOf(length.toString()))
    }
    content.headers.entries().forEach { (key, value) ->
        if (!containsKey(key)) put(key, value)
    }
}
