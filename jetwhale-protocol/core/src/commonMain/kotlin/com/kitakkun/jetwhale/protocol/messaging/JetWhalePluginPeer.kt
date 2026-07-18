package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Default payload format used when none is provided. */
public val DefaultJetWhaleMessagingFormat: StringFormat = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * One symmetric messaging endpoint for one plugin on one connection. Both sides (host and agent)
 * run the same peer; there is no directional difference.
 *
 * This is the composition/facade over the peer's collaborators — each responsibility lives in its
 * own type and nothing else lives here:
 * - correlation: [PendingRequestStore] assigns correlation ids, keeps the pending-request map, and
 *   fails all pending requests on [close]; this peer applies [requestTimeout] around the wait.
 * - inbound dispatch and back-pressure: [InboundFrameDispatcher] owns the arrival-ordered queue,
 *   the concurrency bound on request handlers, the ready gate, and the drop/fast-fail policy.
 *   Replies never enter that queue: they are completed **immediately, off the queue** here, so a
 *   handler awaiting a reply is never blocked behind it.
 * - outgoing ordering: all frames leave through [outgoingQueue], a single writer, preserving send order.
 *
 * The transport is abstract: feed inbound frames to [onFrame], and deliver outbound frames in
 * [sendFrame] (e.g. over a WebSocket).
 *
 * When [awaitReady] is true, inbound notifications and requests are held (in arrival order) until
 * [markReady]; replies always flow, so the preparing side's own requests still complete. The
 * runtimes use this as the `onPrepare` barrier.
 *
 * The inbound/outbound queues are bounded to [bufferCapacity] to avoid unbounded growth under load;
 * a frame that cannot be enqueued is dropped (notifications) or fails its request fast, reported via
 * [logger] (a no-op by default).
 */
public class JetWhalePluginPeer(
    public val pluginId: String,
    parentScope: CoroutineScope,
    private val sendFrame: suspend (PluginFrame) -> Unit,
    private val requestTimeout: Duration = 5.seconds,
    private val payloadFormat: StringFormat = DefaultJetWhaleMessagingFormat,
    private val bufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    private val maxConcurrentRequests: Int = DEFAULT_MAX_CONCURRENT_REQUESTS,
    awaitReady: Boolean = false,
    private val logger: (String) -> Unit = {},
) {
    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    private val handlers = JetWhaleMessageHandlers()

    private val outgoingQueue = Channel<PluginFrame>(capacity = bufferCapacity, onBufferOverflow = BufferOverflow.SUSPEND)

    private val pendingRequestStore = PendingRequestStore()

    private val inboundFrameDispatcher = InboundFrameDispatcher(
        pluginId = pluginId,
        scope = scope,
        handlers = handlers,
        payloadFormat = payloadFormat,
        outgoingQueue = outgoingQueue,
        bufferCapacity = bufferCapacity,
        maxConcurrentRequests = maxConcurrentRequests,
        awaitReady = awaitReady,
        logger = logger,
    )

    init {
        // Single writer: every outbound frame goes through one queue so send order is preserved.
        scope.launch {
            for (frame in outgoingQueue) {
                try {
                    sendFrame(frame)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // The transport is broken (e.g. a half-closed socket). Without this the pump would
                    // die silently and every later request would wait out its full timeout: close the
                    // outbound side and fail pending requests fast instead.
                    logger("JetWhale: transport send failed for plugin '$pluginId'; closing outbound. (${e.message})")
                    outgoingQueue.close()
                    pendingRequestStore.failAll()
                    break
                }
            }
        }
    }

    /** Registers typed handlers. Call before frames start flowing (e.g. in the plugin's setup). */
    public fun configure(block: JetWhaleMessageHandlers.() -> Unit) {
        handlers.block()
    }

    /** The sending face handed to plugin code. Valid for the lifetime of this peer. */
    public val messenger: JetWhaleMessenger = object : JetWhaleMessenger {
        override val payloadFormat: StringFormat get() = this@JetWhalePluginPeer.payloadFormat

        // The peer is the live transport: while it is bound it simply sends. There is no per-peer
        // offline buffer (cross-connection buffering lives in a BufferedMessenger that wraps this),
        // so QUEUE degrades to a best-effort send here and only FAIL distinguishes a closed peer.
        override fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean {
            val result = outgoingQueue.trySend(PluginFrame.Notification(pluginId, messageType, payload))
            if (result.isSuccess) return true
            if (policy == OfflineSendPolicy.FAIL && result.isClosed) throw JetWhaleConnectionClosedException()
            logger("JetWhale: dropped outbound notification '$messageType' for plugin '$pluginId' (${sendFailureReason(result.isClosed)}).")
            return false
        }

        override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = this@JetWhalePluginPeer.requestRaw(messageType, payload, timeout)
    }

    /** Opens handler dispatch. Call once preparation has completed — or failed. Idempotent. */
    public fun markReady() {
        inboundFrameDispatcher.markReady()
    }

    /** Feed every inbound frame addressed to [pluginId] here. */
    public suspend fun onFrame(frame: PluginFrame) {
        require(frame.pluginId == pluginId) {
            "Frame for plugin '${frame.pluginId}' was routed to the peer of plugin '$pluginId'."
        }
        when (frame) {
            // Off-queue on purpose: a notification/request handler may be awaiting this very reply, so
            // it must not queue behind that handler (it would deadlock the serial inbound consumer).
            is PluginFrame.Reply -> completePending(frame)

            // Notifications and requests share one ordered queue so they are dispatched in the order
            // the other side sent them.
            is PluginFrame.Notification, is PluginFrame.Request -> inboundFrameDispatcher.enqueue(frame)
        }
    }

    /** Fails every pending request and stops this peer. Call when the connection closes. */
    public suspend fun close() {
        // Close the queues first so further sends fail fast instead of enqueueing with no consumer.
        outgoingQueue.close()
        inboundFrameDispatcher.close()
        pendingRequestStore.failAll()
        scope.cancel()
    }

    private suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String {
        val effectiveTimeout = timeout ?: requestTimeout
        val correlationId = pendingRequestStore.newCorrelationId()
        val deferred = CompletableDeferred<PluginFrame.Reply>()
        pendingRequestStore.register(correlationId, deferred)

        val sendResult = outgoingQueue.trySend(PluginFrame.Request(pluginId, correlationId, messageType, payload))
        if (!sendResult.isSuccess) {
            pendingRequestStore.remove(correlationId)
            throw if (sendResult.isClosed) {
                JetWhaleConnectionClosedException()
            } else {
                JetWhaleRequestException("Request '$messageType' could not be sent: outgoing buffer is full.")
            }
        }

        try {
            val reply = try {
                withTimeout(effectiveTimeout) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                throw JetWhaleRequestException("Request '$messageType' timed out after $effectiveTimeout", e)
            }
            return when (reply) {
                is PluginFrame.Reply.Success -> reply.payload

                is PluginFrame.Reply.Failure -> throw JetWhaleRequestException(
                    "Request '$messageType' failed on the remote side: ${reply.errorMessage}",
                )
            }
        } finally {
            pendingRequestStore.remove(correlationId)
        }
    }

    private suspend fun completePending(reply: PluginFrame.Reply) {
        if (!pendingRequestStore.complete(reply)) {
            // Late reply after timeout/close, or a correlation bug on the other side.
            logger("JetWhale: dropping reply with unknown correlation id '${reply.inReplyTo}' for plugin '$pluginId'.")
        }
    }

    public companion object {
        /** Default bound for the inbound/outbound frame queues. */
        public const val DEFAULT_BUFFER_CAPACITY: Int = 1024

        /** Default bound for inbound request handlers running concurrently. */
        public const val DEFAULT_MAX_CONCURRENT_REQUESTS: Int = 256
    }
}
