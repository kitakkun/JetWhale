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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.random.Random
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
 * Responsibilities (and nothing else lives here):
 * - correlation: assigns correlation ids, keeps the pending-request map, applies [requestTimeout],
 *   and fails all pending requests on [close]
 * - dispatch: inbound notifications and requests share **one arrival-ordered queue**, so the peer
 *   *starts* handling them in the exact order the other side sent them — a request sent after a
 *   notification is dispatched only once that notification has been handled. A notification runs to
 *   completion before the next inbound frame is dispatched (notifications are **serial**); a request
 *   is then launched on its own coroutine (requests run **concurrently**, up to
 *   [maxConcurrentRequests] in flight) so a handler that itself calls [JetWhaleMessenger.request] in
 *   the opposite direction cannot deadlock the receive loop. Requests beyond that bound are rejected
 *   with a fast [PluginFrame.Reply.Failure] instead of spawning unbounded handlers. Replies are
 *   completed **immediately, off the queue**, so a handler awaiting a reply is never blocked behind it.
 * - outgoing ordering: all frames leave through a single queue, preserving send order
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

    // Notifications and requests share one queue so they are dispatched in arrival order. Replies are
    // never put here: they are completed directly in [onFrame] to avoid blocking a handler that is
    // itself awaiting a reply behind a slow notification.
    private val inboundQueue = Channel<PluginFrame>(capacity = bufferCapacity, onBufferOverflow = BufferOverflow.SUSPEND)

    private val readyGate: CompletableDeferred<Unit> =
        if (awaitReady) CompletableDeferred() else CompletableDeferred(Unit)

    private val pendingMutex = Mutex()
    private val pendingReplies = mutableMapOf<String, CompletableDeferred<PluginFrame.Reply>>()

    // Bounds the number of in-flight inbound request handlers. tryAcquire is non-blocking, so a flood
    // of requests (a buggy plugin, or a misbehaving peer) is rejected fast instead of spawning
    // unbounded handler coroutines — and never stalls the notification lane.
    private val requestSlots = Semaphore(maxConcurrentRequests)

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
                    failAllPending()
                    break
                }
            }
        }
        // Single consumer: notifications and requests are dispatched in arrival order. A notification
        // is handled to completion before the next frame is dispatched; a request is launched on its
        // own coroutine so it runs concurrently (and may request() back) without blocking this loop.
        scope.launch {
            for (frame in inboundQueue) {
                readyGate.await()
                when (frame) {
                    is PluginFrame.Notification -> dispatchNotification(frame)

                    is PluginFrame.Request -> dispatchRequest(frame)

                    // Replies never enter this queue (see onFrame).
                    is PluginFrame.Reply ->
                        logger("JetWhale: unexpected ${frame::class.simpleName} in the inbound queue for plugin '$pluginId'; ignoring.")
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
            logger("JetWhale: dropped outbound notification '$messageType' for plugin '$pluginId' (${queueState(result.isClosed)}).")
            return false
        }

        override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = this@JetWhalePluginPeer.requestRaw(messageType, payload, timeout)
    }

    /** Opens handler dispatch. Call once preparation has completed — or failed. Idempotent. */
    public fun markReady() {
        readyGate.complete(Unit)
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
            is PluginFrame.Notification, is PluginFrame.Request -> enqueueInbound(frame)
        }
    }

    /**
     * Enqueues a notification or request for the serial consumer. trySend, not send: the host routes
     * every session/plugin's frames from one collector, so suspending here would stall delivery for
     * all of them. A full/closed queue drops the notification or fails the request fast instead, so a
     * requester never waits out the timeout for a frame we silently dropped.
     */
    private fun enqueueInbound(frame: PluginFrame) {
        val result = inboundQueue.trySend(frame)
        if (result.isSuccess) return
        when (frame) {
            is PluginFrame.Request -> outgoingQueue.trySend(
                PluginFrame.Reply.Failure(
                    pluginId = pluginId,
                    inReplyTo = frame.correlationId,
                    errorMessage = "Request '${frame.messageType}' could not be dispatched (${queueState(result.isClosed)}).",
                ),
            )

            is PluginFrame.Notification ->
                logger("JetWhale: dropped inbound notification '${frame.messageType}' for plugin '$pluginId' (${queueState(result.isClosed)}).")

            // Only notifications and requests reach this path (see onFrame).
            is PluginFrame.Reply -> Unit
        }
    }

    /** Fails every pending request and stops this peer. Call when the connection closes. */
    public suspend fun close() {
        // Close the queues first so further sends fail fast instead of enqueueing with no consumer.
        outgoingQueue.close()
        inboundQueue.close()
        failAllPending()
        scope.cancel()
    }

    private suspend fun failAllPending() {
        pendingMutex.withLock {
            pendingReplies.values.forEach { it.completeExceptionally(JetWhaleConnectionClosedException()) }
            pendingReplies.clear()
        }
    }

    private suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String {
        val effectiveTimeout = timeout ?: requestTimeout
        val correlationId = generateCorrelationId()
        val deferred = CompletableDeferred<PluginFrame.Reply>()
        pendingMutex.withLock { pendingReplies[correlationId] = deferred }

        val sendResult = outgoingQueue.trySend(PluginFrame.Request(pluginId, correlationId, messageType, payload))
        if (!sendResult.isSuccess) {
            pendingMutex.withLock { pendingReplies.remove(correlationId) }
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
            pendingMutex.withLock { pendingReplies.remove(correlationId) }
        }
    }

    private suspend fun completePending(reply: PluginFrame.Reply) {
        val deferred = pendingMutex.withLock { pendingReplies.remove(reply.inReplyTo) }
        if (deferred == null) {
            // Late reply after timeout/close, or a correlation bug on the other side.
            logger("JetWhale: dropping reply with unknown correlation id '${reply.inReplyTo}' for plugin '$pluginId'.")
            return
        }
        deferred.complete(reply)
    }

    /**
     * Launches a handler for [frame] if a request slot is free, releasing it when the handler
     * finishes. If the in-flight bound is reached, rejects the request fast (no coroutine spawned)
     * so the requester gets a [PluginFrame.Reply.Failure] instead of waiting out its timeout.
     */
    private fun dispatchRequest(frame: PluginFrame.Request) {
        if (!requestSlots.tryAcquire()) {
            logger("JetWhale: rejecting request '${frame.messageType}' for plugin '$pluginId' ($maxConcurrentRequests concurrent requests in flight).")
            outgoingQueue.trySend(
                PluginFrame.Reply.Failure(
                    pluginId = pluginId,
                    inReplyTo = frame.correlationId,
                    errorMessage = "Too many concurrent requests in flight (max $maxConcurrentRequests).",
                ),
            )
            return
        }
        scope.launch {
            try {
                handleRequest(frame)
            } finally {
                requestSlots.release()
            }
        }
    }

    private suspend fun handleRequest(frame: PluginFrame.Request) {
        val entry = handlers.requestEntryFor(frame.messageType)
        val reply: PluginFrame.Reply = if (entry == null) {
            PluginFrame.Reply.Failure(
                pluginId = pluginId,
                inReplyTo = frame.correlationId,
                errorMessage = "No request handler registered for '${frame.messageType}'",
            )
        } else {
            try {
                val request = payloadFormat.decodeFromString(entry.requestSerializer.castToAny(), frame.payload)
                val result = entry.handler(request)
                PluginFrame.Reply.Success(
                    pluginId = pluginId,
                    inReplyTo = frame.correlationId,
                    payload = payloadFormat.encodeToString(entry.replySerializer.castToAny(), result),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PluginFrame.Reply.Failure(
                    pluginId = pluginId,
                    inReplyTo = frame.correlationId,
                    errorMessage = e.message ?: (e::class.simpleName ?: "Unknown error"),
                )
            }
        }
        val result = outgoingQueue.trySend(reply)
        if (!result.isSuccess) {
            logger("JetWhale: dropped reply to '${frame.messageType}' for plugin '$pluginId' (${queueState(result.isClosed)}).")
        }
    }

    private suspend fun dispatchNotification(frame: PluginFrame.Notification) {
        val entry = handlers.eventEntryFor(frame.messageType)
        if (entry == null) {
            // Forward-compatibility: an unknown event (e.g. version skew) is skipped, not fatal.
            logger("JetWhale: no event handler registered for '${frame.messageType}' (plugin '$pluginId'); skipping.")
            return
        }
        try {
            val event = payloadFormat.decodeFromString(entry.serializer.castToAny(), frame.payload)
            entry.handler(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            logger("JetWhale: failed to decode event '${frame.messageType}' (plugin '$pluginId'): ${e.message}")
        } catch (e: Throwable) {
            logger("JetWhale: event handler for '${frame.messageType}' (plugin '$pluginId') failed: ${e.message}")
        }
    }

    private fun queueState(closed: Boolean): String = if (closed) "peer closed" else "buffer full"

    private fun generateCorrelationId(): String = Random.nextLong().toULong().toString(16).padStart(16, '0') +
        Random.nextLong().toULong().toString(16).padStart(16, '0')

    public companion object {
        /** Default bound for the inbound/outbound frame queues. */
        public const val DEFAULT_BUFFER_CAPACITY: Int = 1024

        /** Default bound for inbound request handlers running concurrently. */
        public const val DEFAULT_MAX_CONCURRENT_REQUESTS: Int = 256
    }
}

@Suppress("UNCHECKED_CAST")
private fun KSerializer<*>.castToAny(): KSerializer<Any> = this as KSerializer<Any>
