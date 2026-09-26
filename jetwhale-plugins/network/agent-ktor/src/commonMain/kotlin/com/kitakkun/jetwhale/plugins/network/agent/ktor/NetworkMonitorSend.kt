package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.bodyBytes
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
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
import kotlin.time.TimeMark
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
    limits: BodyCaptureLimits,
    proceed: suspend (HttpRequestBuilder) -> HttpClientCall,
): HttpClientCall {
    val txId = newTransactionId()
    val started = TimeSource.Monotonic.markNow()
    val mock = recordRequestAndFindMock(request, txId, limits)

    val call = sendOrServeMock(client, request, mock, txId, started, proceed)

    val (callToReturn, body) = captureResponseBodySafely(call, limits)
    recordResponse(
        CapturedHttpResponse(
            txId = txId,
            statusCode = callToReturn.response.status.value,
            statusDescription = callToReturn.response.status.description,
            headers = callToReturn.response.headers.toCapturedMap(),
            body = body.text,
            bodyTruncated = body.truncated,
            bodyEncoding = body.encoding,
            durationMs = started.elapsedNow().inWholeMilliseconds,
            fromMock = mock != null,
        ),
    )
    return callToReturn
}

/** Records the outgoing request, then answers with the mock registered for it, if any. */
private fun JetWhaleNetworkAgentPlugin.recordRequestAndFindMock(
    request: HttpRequestBuilder,
    txId: String,
    limits: BodyCaptureLimits,
): MockResponseSpec? {
    val method = request.method.value
    val url = request.url.buildString()
    recordRequest(request, txId = txId, method = method, url = url, limits = limits)
    return findMock(method, url)
}

private fun JetWhaleNetworkAgentPlugin.recordRequest(request: HttpRequestBuilder, txId: String, method: String, url: String, limits: BodyCaptureLimits) {
    val body = captureRequestBodySafely(request.body, limits)
    recordRequest(
        CapturedHttpRequest(
            txId = txId,
            method = method,
            url = url,
            headers = request.capturedRequestHeaders(),
            body = body.text,
            bodyTruncated = body.truncated,
            bodyEncoding = body.encoding,
            timestampMs = GMTDate().timestamp,
        ),
    )
}

/**
 * Serves [mock] when there is one; otherwise continues the send, recording a thrown failure
 * before rethrowing it.
 */
private suspend fun JetWhaleNetworkAgentPlugin.sendOrServeMock(
    client: HttpClient,
    request: HttpRequestBuilder,
    mock: MockResponseSpec?,
    txId: String,
    started: TimeMark,
    proceed: suspend (HttpRequestBuilder) -> HttpClientCall,
): HttpClientCall {
    if (mock != null) return serveMock(client, request, mock)
    return try {
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
        body = ByteReadChannel(mock.bodyBytes()),
        callContext = parentContext + Job(parentContext[Job]),
    )
    return HttpClientCall(client, request.build(), responseData)
}

private fun StringValues.toCapturedMap(): Map<String, List<String>> = entries().associate { it.key to it.value }

/**
 * Captures the request headers visible at the send phase, enriched with the body's
 * Content-Type / Content-Length and any body-level headers. Headers injected later by the engine
 * (e.g. User-Agent, Accept-Encoding, Host) are not visible here and are omitted.
 */
private fun HttpRequestBuilder.capturedRequestHeaders(): Map<String, List<String>> = buildMap {
    putAll(headers.build().toCapturedMap())
    val content = body as? OutgoingContent ?: return@buildMap
    content.contentType?.let { putIfAbsentIgnoringCase("Content-Type", listOf(it.toString())) }
    content.contentLength?.let { putIfAbsentIgnoringCase("Content-Length", listOf(it.toString())) }
    content.headers.entries().forEach { (key, value) -> putIfAbsentIgnoringCase(key, value) }
}

/**
 * Adds a body-derived header only when the request doesn't already carry it. The comparison ignores
 * case because header names are case-insensitive but this map keeps whatever spelling the request
 * used, so a case-sensitive check would let `content-length` and `Content-Length` both through.
 */
private fun MutableMap<String, List<String>>.putIfAbsentIgnoringCase(name: String, values: List<String>) {
    if (keys.none { it.equals(name, ignoreCase = true) }) put(name, values)
}
