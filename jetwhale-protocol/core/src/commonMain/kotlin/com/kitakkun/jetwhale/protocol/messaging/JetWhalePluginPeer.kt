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
 * - dispatch: notifications are handled **serially in arrival order** (one lane), requests are
 *   handled **concurrently** (one coroutine each) so a handler that itself calls
 *   [JetWhaleMessenger.request] in the opposite direction cannot deadlock the receive loop
 * - outgoing ordering: all frames leave through a single queue, preserving send order
 *
 * The transport is abstract: feed inbound frames to [onFrame], and deliver outbound frames in
 * [sendFrame] (e.g. over a WebSocket).
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
    private val logger: (String) -> Unit = {},
) {
    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    private val handlers = JetWhaleMessagingHandlers()

    private val outgoingQueue = Channel<PluginFrame>(capacity = bufferCapacity, onBufferOverflow = BufferOverflow.SUSPEND)
    private val notificationQueue = Channel<PluginFrame.Notification>(capacity = bufferCapacity, onBufferOverflow = BufferOverflow.SUSPEND)

    private val pendingMutex = Mutex()
    private val pendingReplies = mutableMapOf<String, CompletableDeferred<PluginFrame.Reply>>()

    init {
        // Single writer: every outbound frame goes through one queue so send order is preserved.
        scope.launch {
            for (frame in outgoingQueue) {
                sendFrame(frame)
            }
        }
        // Single consumer: notifications are dispatched serially, in arrival order.
        scope.launch {
            for (frame in notificationQueue) {
                dispatchNotification(frame)
            }
        }
    }

    /** Registers typed handlers. Call before frames start flowing (e.g. in the plugin's setup). */
    public fun configure(block: JetWhaleMessagingHandlers.() -> Unit) {
        handlers.block()
    }

    /** The sending face handed to plugin code. Valid for the lifetime of this peer. */
    public val messenger: JetWhaleMessenger = object : JetWhaleMessenger {
        override val coroutineScope: CoroutineScope get() = scope
        override val payloadFormat: StringFormat get() = this@JetWhalePluginPeer.payloadFormat

        override fun sendRaw(messageType: String, payload: String) {
            val result = outgoingQueue.trySend(PluginFrame.Notification(pluginId, messageType, payload))
            if (!result.isSuccess) {
                logger("JetWhale: dropped outbound notification '$messageType' for plugin '$pluginId' (${queueState(result.isClosed)}).")
            }
        }

        override suspend fun requestRaw(messageType: String, payload: String): String = this@JetWhalePluginPeer.requestRaw(messageType, payload)
    }

    /** Feed every inbound frame addressed to [pluginId] here. */
    public suspend fun onFrame(frame: PluginFrame) {
        require(frame.pluginId == pluginId) {
            "Frame for plugin '${frame.pluginId}' was routed to the peer of plugin '$pluginId'."
        }
        when (frame) {
            // trySend, not send: the host routes every session/plugin's frames from one collector, so
            // suspending here would stall delivery for all of them. A full/closed queue drops instead.
            is PluginFrame.Notification -> {
                val result = notificationQueue.trySend(frame)
                if (!result.isSuccess) {
                    logger("JetWhale: dropped inbound notification '${frame.messageType}' for plugin '$pluginId' (${queueState(result.isClosed)}).")
                }
            }

            // One coroutine per request: a handler may request() in the opposite direction without
            // blocking this receive path.
            is PluginFrame.Request -> scope.launch { handleRequest(frame) }

            is PluginFrame.Reply -> completePending(frame)
        }
    }

    /** Fails every pending request and stops this peer. Call when the connection closes. */
    public suspend fun close() {
        // Close the queues first so further sends fail fast instead of enqueueing with no consumer.
        outgoingQueue.close()
        notificationQueue.close()
        pendingMutex.withLock {
            pendingReplies.values.forEach { it.completeExceptionally(JetWhaleConnectionClosedException()) }
            pendingReplies.clear()
        }
        scope.cancel()
    }

    private suspend fun requestRaw(messageType: String, payload: String): String {
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
                withTimeout(requestTimeout) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                throw JetWhaleRequestException("Request '$messageType' timed out after $requestTimeout", e)
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
    }
}

@Suppress("UNCHECKED_CAST")
private fun KSerializer<*>.castToAny(): KSerializer<Any> = this as KSerializer<Any>
