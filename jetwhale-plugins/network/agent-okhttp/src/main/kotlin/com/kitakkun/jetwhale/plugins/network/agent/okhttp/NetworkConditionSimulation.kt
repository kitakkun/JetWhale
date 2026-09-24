package com.kitakkun.jetwhale.plugins.network.agent.okhttp

import com.kitakkun.jetwhale.plugins.network.agent.BandwidthPacer
import com.kitakkun.jetwhale.plugins.network.agent.NetworkConditionPlan
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Sink
import okio.Source
import okio.buffer
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.TimeSource

/**
 * Carries out what [plan] does before the exchange — fails an offline request at once, waits the
 * latency, injects a failure — and returns the request to send, its body paced when the plan caps
 * the upload. The exceptions are the ones OkHttp itself throws for the same situations, so the
 * app's error handling sees what it would on a real network.
 */
internal fun simulateNetworkBeforeExchange(plan: NetworkConditionPlan, request: Request): Request {
    val host = request.url.host
    val cause = "simulated by JetWhale rule '${plan.ruleName}'"
    if (plan.offline) throw UnknownHostException("Unable to resolve host \"$host\": the network is offline ($cause)")
    Thread.sleep(plan.latency.inWholeMilliseconds)
    plan.failure?.let { failure ->
        Thread.sleep(plan.failureAfter.inWholeMilliseconds)
        throw failure.toException(host, cause)
    }
    val uploadRate = plan.uploadBytesPerSecond ?: return request
    val body = request.body ?: return request
    return request.newBuilder().method(request.method, PacedRequestBody(body, uploadRate)).build()
}

private fun InjectedFailure.toException(host: String, cause: String): IOException = when (this) {
    InjectedFailure.TIMEOUT -> SocketTimeoutException("timeout reading from $host ($cause)")
    InjectedFailure.CONNECTION_RESET -> SocketException("Connection reset ($cause)")
    InjectedFailure.UNREACHABLE -> ConnectException("Failed to connect to $host: Network is unreachable ($cause)")
}

/**
 * This response with its body paced to [bytesPerSecond] as the caller reads it. A WebSocket upgrade
 * is left alone: its body is the live frame stream, which pacing here would corrupt.
 */
internal fun Response.withBodyPacedTo(bytesPerSecond: Long): Response {
    if (isWebSocketUpgrade()) return this
    val original = body
    return newBuilder().body(PacedResponseBody(original, bytesPerSecond)).build()
}

private class PacedRequestBody(private val delegate: RequestBody, private val bytesPerSecond: Long) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun isDuplex(): Boolean = delegate.isDuplex()

    override fun writeTo(sink: BufferedSink) {
        val paced = PacedSink(sink, BandwidthPacer(bytesPerSecond, TimeSource.Monotonic)).buffer()
        delegate.writeTo(paced)
        paced.emit()
    }
}

private class PacedResponseBody(private val delegate: ResponseBody, private val bytesPerSecond: Long) : ResponseBody() {
    private val source: BufferedSource by lazy {
        PacedSource(delegate.source(), BandwidthPacer(bytesPerSecond, TimeSource.Monotonic)).buffer()
    }

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource = source

    override fun close() {
        delegate.close()
    }
}

/** Hands on at most one pacer chunk per read, and holds it back until the rate allows it. */
private class PacedSource(delegate: Source, private val pacer: BandwidthPacer) : ForwardingSource(delegate) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, minOf(byteCount, pacer.chunkSize.toLong()))
        if (read > 0) Thread.sleep(pacer.delayBefore(read.toInt()).inWholeMilliseconds)
        return read
    }
}

/** Passes writes on in pacer chunks, waiting before each until the rate allows it. */
private class PacedSink(delegate: Sink, private val pacer: BandwidthPacer) : ForwardingSink(delegate) {
    override fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val chunk = minOf(remaining, pacer.chunkSize.toLong())
            Thread.sleep(pacer.delayBefore(chunk.toInt()).inWholeMilliseconds)
            super.write(source, chunk)
            super.flush()
            remaining -= chunk
        }
    }
}
