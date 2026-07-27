package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.StringValues
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Runs one request/response round trip through the agent: records the request, serves a matching
 * mock instead of hitting the network, and records the response or failure.
 *
 * Shared by both entry points of this module. [proceed] is the caller's way to continue the send —
 * `Send.Sender.proceed` for the client plugin, `Sender.execute` for the [io.ktor.client.plugins.HttpSend]
 * interceptor. [client] is needed to synthesize a mocked [HttpClientCall] and must be the client the
 * request is running on.
 */
internal suspend fun JetWhaleNetworkAgentPlugin.monitorSend(
    client: HttpClient,
    request: HttpRequestBuilder,
    maxBodyChars: Int,
    proceed: suspend (HttpRequestBuilder) -> HttpClientCall,
): HttpClientCall {
    val txId = newTransactionId()
    val started = TimeSource.Monotonic.markNow()
    val method = request.method.value
    val url = request.url.buildString()

    recordRequest(request, txId, method, url, maxBodyChars)
    val mock = findMock(method, url)

    val call = if (mock != null) {
        serveMock(client, request, mock)
    } else {
        try {
            proceed(request)
        } catch (e: Throwable) {
            recordFailure(
                HttpRequestFailure(
                    txId = txId,
                    message = e.message ?: e.toString(),
                    durationMs = started.elapsedNow().inWholeMilliseconds,
                ),
            )
            throw e
        }
    }

    val (callToReturn, body) = captureResponseBodySafely(call, maxBodyChars)
    recordResponse(
        CapturedHttpResponse(
            txId = txId,
            statusCode = callToReturn.response.status.value,
            statusDescription = callToReturn.response.status.description,
            headers = callToReturn.response.headers.toCapturedMap(),
            body = body.text,
            bodyTruncated = body.truncated,
            durationMs = started.elapsedNow().inWholeMilliseconds,
            fromMock = mock != null,
        ),
    )
    return callToReturn
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
    // Ktor completes the call context's Job when the response is done, so it must be a
    // CompletableJob. The Send-pipeline coroutine's own Job is a StandaloneCoroutine, and handing
    // that over crashes with "StandaloneCoroutine cannot be cast to CompletableJob" the moment the
    // caller reads the mocked response — so give the call its own Job(parent), exactly as a real
    // client engine builds its call context.
    val parentContext = currentCoroutineContext()
    val responseData = HttpResponseData(
        statusCode = HttpStatusCode.fromValue(mock.statusCode),
        requestTime = GMTDate(),
        headers = HeadersBuilder().apply {
            mock.headers.forEach { (key, value) -> append(key, value) }
            // A mock without a Content-Type synthesizes a response whose header is null, which makes
            // a client using ContentNegotiation reject the body. Default it so headerless JSON mocks
            // stay usable, without overriding a Content-Type the mock already sets.
            if (mock.headers.keys.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }) {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
        }.build(),
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(mock.body.encodeToByteArray()),
        callContext = parentContext + Job(parentContext[Job]),
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
    return saved to saved.response.bodyAsText().truncate(maxChars)
}

private fun StringValues.toCapturedMap(): Map<String, List<String>> = entries().associate { it.key to it.value }

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun HttpResponse.isWebSocketUpgrade(): Boolean = status == HttpStatusCode.SwitchingProtocols && headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true

/**
 * Captures the request headers visible at the send phase, enriched with the body's
 * Content-Type / Content-Length and any body-level headers. Headers injected later by the engine
 * (e.g. User-Agent, Accept-Encoding, Host) are not visible here and are omitted.
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
