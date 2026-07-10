package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
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
import kotlinx.coroutines.delay
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
 * @param maxBodyChars request/response bodies longer than this are truncated for transport.
 */
@OptIn(InternalAPI::class) // HttpClientCall's constructor is needed to synthesize mock responses.
fun JetWhaleNetworkAgentPlugin.ktorClientPlugin(maxBodyChars: Int = 100_000): ClientPlugin<Unit> {
    val agent = this
    return createClientPlugin("JetWhaleNetworkMonitor") {
        on(Send) { request ->
            val txId = agent.newTransactionId()
            val started = TimeSource.Monotonic.markNow()
            val method = request.method.value
            val url = request.url.buildString()

            val requestBody = captureOutgoingBody(request.body, maxBodyChars)
            agent.recordRequest(
                CapturedHttpRequest(
                    txId = txId,
                    method = method,
                    url = url,
                    headers = request.capturedRequestHeaders(),
                    body = requestBody.text,
                    bodyTruncated = requestBody.truncated,
                    timestampMs = GMTDate().timestamp,
                ),
            )

            agent.findMock(method, url)?.let { mock ->
                if (mock.delayMs > 0) delay(mock.delayMs)
                val status = HttpStatusCode.fromValue(mock.statusCode)
                agent.recordResponse(
                    CapturedHttpResponse(
                        txId = txId,
                        statusCode = mock.statusCode,
                        statusDescription = status.description,
                        headers = mock.headers.mapValues { listOf(it.value) },
                        body = mock.body,
                        durationMs = started.elapsedNow().inWholeMilliseconds,
                        fromMock = true,
                    ),
                )
                val responseData = HttpResponseData(
                    statusCode = status,
                    requestTime = GMTDate(),
                    headers = HeadersBuilder().apply {
                        mock.headers.forEach { (key, value) -> append(key, value) }
                    }.build(),
                    version = HttpProtocolVersion.HTTP_1_1,
                    body = ByteReadChannel(mock.body.encodeToByteArray()),
                    callContext = this.coroutineContext,
                )
                return@on HttpClientCall(client, request.build(), responseData)
            }

            try {
                val rawCall = proceed(request)
                val rawResponse = rawCall.response

                // A WebSocket upgrade response (101) has no conventional body — by the time this
                // plugin sees it, the connection has already switched to the raw frame stream, so
                // save()/bodyAsText() would read live WebSocket frames as if they were an HTTP
                // body, corrupting the frame stream for the caller. Record a placeholder instead
                // of touching the body, and return the untouched call.
                if (rawResponse.isWebSocketUpgrade()) {
                    agent.recordResponse(
                        CapturedHttpResponse(
                            txId = txId,
                            statusCode = rawResponse.status.value,
                            statusDescription = rawResponse.status.description,
                            headers = rawResponse.headers.toCapturedMap(),
                            body = "<websocket upgrade>",
                            bodyTruncated = false,
                            durationMs = started.elapsedNow().inWholeMilliseconds,
                            fromMock = false,
                        ),
                    )
                    return@on rawCall
                }

                // save() buffers the body so we can read it for inspection and still hand a
                // fresh, readable response to the caller.
                val call = rawCall.save()
                val response = call.response
                val responseBody = response.bodyAsText().truncate(maxBodyChars)
                agent.recordResponse(
                    CapturedHttpResponse(
                        txId = txId,
                        statusCode = response.status.value,
                        statusDescription = response.status.description,
                        headers = response.headers.toCapturedMap(),
                        body = responseBody.text,
                        bodyTruncated = responseBody.truncated,
                        durationMs = started.elapsedNow().inWholeMilliseconds,
                        fromMock = false,
                    ),
                )
                call
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
    }
}

private data class BodyCapture(val text: String?, val truncated: Boolean)

private fun String.truncate(max: Int): BodyCapture = if (length <= max) BodyCapture(this, false) else BodyCapture(substring(0, max), true)

private fun captureOutgoingBody(content: Any?, maxChars: Int): BodyCapture = when (content) {
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString().truncate(maxChars)

    // Channel/stream bodies cannot be read here without consuming them; describe them instead.
    is OutgoingContent -> BodyCapture(content.contentType?.let { "<$it>" }, false)

    else -> BodyCapture(null, false)
}

private fun StringValues.toCapturedMap(): Map<String, List<String>> = entries().associate { it.key to it.value }

/** True for a successful WebSocket upgrade response (101 Switching Protocols + `Upgrade: websocket`). */
private fun HttpResponse.isWebSocketUpgrade(): Boolean =
    status == HttpStatusCode.SwitchingProtocols && headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true

/**
 * Captures the request headers visible at the [Send] phase, enriched with the body's
 * Content-Type / Content-Length and any body-level headers. Headers injected later by the engine
 * (e.g. User-Agent, Accept-Encoding, Host) are not visible to a client plugin and are omitted.
 */
private fun HttpRequestBuilder.capturedRequestHeaders(): Map<String, List<String>> {
    val result = LinkedHashMap<String, List<String>>()
    headers.build().entries().forEach { (key, value) -> result[key] = value }
    val content = body
    if (content is OutgoingContent) {
        content.contentType?.let { type ->
            if ("Content-Type" !in result) result["Content-Type"] = listOf(type.toString())
        }
        content.contentLength?.let { length ->
            if ("Content-Length" !in result) result["Content-Length"] = listOf(length.toString())
        }
        content.headers.entries().forEach { (key, value) ->
            if (key !in result) result[key] = value
        }
    }
    return result
}
