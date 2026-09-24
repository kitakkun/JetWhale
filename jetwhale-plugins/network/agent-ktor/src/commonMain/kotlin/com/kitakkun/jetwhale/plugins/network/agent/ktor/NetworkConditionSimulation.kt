package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.plugins.network.agent.BandwidthPacer
import com.kitakkun.jetwhale.plugins.network.agent.NetworkConditionPlan
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.replaceResponse
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import io.ktor.client.request.setBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlin.time.TimeSource

/**
 * Carries out what [plan] does before the exchange: fails an offline request at once, waits the
 * latency, injects a failure, and paces the request body. A mocked request is never sent, so its
 * body is not paced.
 */
internal suspend fun simulateNetworkBeforeExchange(plan: NetworkConditionPlan, request: HttpRequestBuilder, url: String, mocked: Boolean) {
    if (plan.offline) throw IOException("Unable to resolve host for $url: the network is offline (simulated by JetWhale rule '${plan.ruleName}')")
    delay(plan.latency)
    plan.failure?.let { failure ->
        delay(plan.failureAfter)
        throw failure.toException(url, plan.ruleName)
    }
    val uploadRate = plan.uploadBytesPerSecond ?: return
    val body = request.body
    if (!mocked && body is OutgoingContent) body.pacedTo(uploadRate)?.let(request::setBody)
}

private fun InjectedFailure.toException(url: String, ruleName: String): IOException {
    val cause = "simulated by JetWhale rule '$ruleName'"
    return when (this) {
        InjectedFailure.TIMEOUT -> SocketTimeoutException("Socket timeout reading $url ($cause)")
        InjectedFailure.CONNECTION_RESET -> IOException("Connection reset while requesting $url ($cause)")
        InjectedFailure.UNREACHABLE -> IOException("Failed to connect to $url: network is unreachable ($cause)")
    }
}

/**
 * This call with its response body paced to [bytesPerSecond]. A WebSocket upgrade is left alone:
 * its body is the live frame stream, which pacing here would corrupt.
 */
@OptIn(InternalAPI::class) // rawContent is the undecoded body replaceResponse expects to wrap.
internal fun HttpClientCall.withResponseBodyPacedTo(bytesPerSecond: Long): HttpClientCall {
    if (response.isWebSocketUpgrade()) return this
    return replaceResponse {
        val source = rawContent
        writer { copyPaced(source, channel, bytesPerSecond, TimeSource.Monotonic) }.channel
    }
}

/**
 * This request body paced to [bytesPerSecond], or null for a body that has no bytes to pace (no
 * content, a protocol upgrade).
 */
private fun OutgoingContent.pacedTo(bytesPerSecond: Long): OutgoingContent? {
    val original = this
    val openSource: (CoroutineScope) -> ByteReadChannel = when (original) {
        is OutgoingContent.ByteArrayContent -> { _ -> ByteReadChannel(original.bytes()) }
        is OutgoingContent.ReadChannelContent -> { _ -> original.readFrom() }
        is OutgoingContent.WriteChannelContent -> { scope -> scope.writer { original.writeTo(channel) }.channel }
        else -> return null
    }
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType get() = original.contentType
        override val contentLength get() = original.contentLength
        override val status get() = original.status
        override val headers get() = original.headers

        override suspend fun writeTo(channel: ByteWriteChannel) = coroutineScope {
            copyPaced(openSource(this), channel, bytesPerSecond, TimeSource.Monotonic)
        }
    }
}

/** Copies [source] to [destination] no faster than [bytesPerSecond], in the pacer's chunks. */
internal suspend fun copyPaced(source: ByteReadChannel, destination: ByteWriteChannel, bytesPerSecond: Long, timeSource: TimeSource) {
    val pacer = BandwidthPacer(bytesPerSecond, timeSource)
    val buffer = ByteArray(pacer.chunkSize)
    while (true) {
        val read = source.readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        if (read == 0) continue
        delay(pacer.delayBefore(read))
        destination.writeFully(buffer, 0, read)
        destination.flush()
    }
}
