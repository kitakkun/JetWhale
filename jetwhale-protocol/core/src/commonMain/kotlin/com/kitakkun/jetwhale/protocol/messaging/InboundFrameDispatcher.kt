package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat

/**
 * The inbound dispatch and back-pressure policy for one [JetWhalePluginPeer].
 *
 * Notifications and requests share **one arrival-ordered queue**, so the peer *starts* handling them
 * in the exact order the other side sent them. A notification runs to completion before the next
 * inbound frame is dispatched (notifications are **serial**); a request is then launched on its own
 * coroutine (requests run **concurrently**, up to [maxConcurrentRequests] in flight) so a handler
 * that itself calls `request` in the opposite direction cannot deadlock the receive
 * loop. Requests beyond that bound are rejected with a fast [PluginFrame.Reply.Failure] instead of
 * spawning unbounded handlers.
 *
 * When [awaitReady] is true, dispatch is held (in arrival order) until [markReady]; the peer keeps
 * completing replies off-queue so the preparing side's own requests still complete.
 *
 * Replies produced by handlers (and fast-fail failures) are handed back to the peer's single
 * outbound queue via [outgoingQueue].
 */
internal class InboundFrameDispatcher(
    private val pluginId: String,
    private val scope: CoroutineScope,
    private val handlers: JetWhaleMessageHandlers,
    private val payloadFormat: StringFormat,
    private val outgoingQueue: SendChannel<PluginFrame>,
    bufferCapacity: Int,
    private val maxConcurrentRequests: Int,
    awaitReady: Boolean,
    private val logger: (String) -> Unit,
) {
    // Notifications and requests share one queue so they are dispatched in arrival order. Replies are
    // never put here: the peer completes them directly to avoid blocking a handler that is itself
    // awaiting a reply behind a slow notification.
    private val inboundQueue = Channel<PluginFrame>(capacity = bufferCapacity, onBufferOverflow = BufferOverflow.SUSPEND)

    private val readyGate: CompletableDeferred<Unit> =
        if (awaitReady) CompletableDeferred() else CompletableDeferred(Unit)

    // Bounds the number of in-flight inbound request handlers. tryAcquire is non-blocking, so a flood
    // of requests (a buggy plugin, or a misbehaving peer) is rejected fast instead of spawning
    // unbounded handler coroutines — and never stalls the notification lane.
    private val requestSlots = Semaphore(maxConcurrentRequests)

    init {
        // Single consumer: notifications and requests are dispatched in arrival order. A notification
        // is handled to completion before the next frame is dispatched; a request is launched on its
        // own coroutine so it runs concurrently (and may request() back) without blocking this loop.
        scope.launch {
            for (frame in inboundQueue) {
                readyGate.await()
                when (frame) {
                    is PluginFrame.Notification -> dispatchNotification(frame)

                    is PluginFrame.Request -> dispatchRequest(frame)

                    // Replies never enter this queue (see enqueue).
                    is PluginFrame.Reply ->
                        logger("JetWhale: unexpected ${frame::class.simpleName} in the inbound queue for plugin '$pluginId'; ignoring.")
                }
            }
        }
    }

    /** Opens handler dispatch. Idempotent. */
    fun markReady() {
        readyGate.complete(Unit)
    }

    /** Stops accepting inbound frames. */
    fun close() {
        inboundQueue.close()
    }

    /**
     * Enqueues a notification or request for the serial consumer. trySend, not send: the host routes
     * every session/plugin's frames from one collector, so suspending here would stall delivery for
     * all of them. A full/closed queue drops the notification or fails the request fast instead, so a
     * requester never waits out the timeout for a frame we silently dropped.
     */
    fun enqueue(frame: PluginFrame) {
        val result = inboundQueue.trySend(frame)
        if (result.isSuccess) return
        when (frame) {
            is PluginFrame.Request -> outgoingQueue.trySend(
                PluginFrame.Reply.Failure(
                    pluginId = pluginId,
                    inReplyTo = frame.correlationId,
                    errorMessage = "Request '${frame.messageType}' could not be dispatched (${sendFailureReason(result.isClosed)}).",
                ),
            )

            is PluginFrame.Notification ->
                logger("JetWhale: dropped inbound notification '${frame.messageType}' for plugin '$pluginId' (${sendFailureReason(result.isClosed)}).")

            // Only notifications and requests reach this path (see JetWhalePluginPeer.onFrame).
            is PluginFrame.Reply -> Unit
        }
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
            logger("JetWhale: dropped reply to '${frame.messageType}' for plugin '$pluginId' (${sendFailureReason(result.isClosed)}).")
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
}

/** Human-readable reason a `trySend` failed, for the frame-dropped/undispatched log lines. */
internal fun sendFailureReason(closed: Boolean): String = if (closed) "peer closed" else "buffer full"

@Suppress("UNCHECKED_CAST")
internal fun KSerializer<*>.castToAny(): KSerializer<Any> = this as KSerializer<Any>
