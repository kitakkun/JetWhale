package com.kitakkun.jetwhale.agent.sdk.messaging

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleConnectionClosedException
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleTransportMessenger
import com.kitakkun.jetwhale.protocol.messaging.RawSendOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.StringFormat
import kotlin.time.Duration

/**
 * A connection-independent [JetWhaleOfflineCapableMessenger] that outlives any single connection. Plugin code holds
 * one of these for its whole lifetime and the runtime [bind]s/[unbind]s the live transport (a peer's
 * messenger) underneath as connections come and go.
 *
 * Its reason to exist is the [OfflineSendPolicy.QUEUE] path: events sent while no transport is bound
 * are held in a **bounded, drop-oldest** buffer and flushed, in arrival order, once a transport is
 * bound *and* flushing has been opened with [startFlush]. The gate lets the runtime run a plugin's
 * connection-time initialization (which may `request` the other side) before buffered events start
 * flowing. [OfflineSendPolicy.DROP] and [OfflineSendPolicy.FAIL] sends and all requests go straight to
 * the live transport (or are dropped / fail / throw when there is none) — they are never buffered or
 * gated, so initialization can request() freely.
 *
 * Concurrency: sends arrive from arbitrary app threads while [bind]/[unbind] run on the runtime's
 * messaging coroutine. The live reference is a [MutableStateFlow] (atomic reads) and the offline
 * buffer is a single [Channel] drained by one consumer, so queued events keep a single FIFO order
 * without a lock. (Cross-policy ordering is not guaranteed: a [DROP][OfflineSendPolicy.DROP] /
 * [FAIL][OfflineSendPolicy.FAIL] send may overtake queued events still draining. Use one policy per
 * event stream if order matters.)
 *
 * @param bufferCapacity max number of offline events to retain; `0` disables buffering (QUEUE then
 *   behaves like `trySend`).
 */
public class BufferedMessenger(
    parentScope: CoroutineScope,
    override val payloadFormat: StringFormat,
    private val bufferCapacity: Int,
    private val logger: (String) -> Unit = {},
) : JetWhaleOfflineCapableMessenger {
    private data class BufferedEvent(val messageType: String, val payload: String)

    private val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    /** The live transport, or null while disconnected. Sole writer is [bind]/[unbind]. */
    private val live = MutableStateFlow<JetWhaleTransportMessenger?>(null)

    /** Whether buffered events may flush. Opened by [startFlush] (after init), reset by [unbind]. */
    private val flushOpen = MutableStateFlow(false)

    /** The offline buffer: a single bounded queue, drop-oldest. Null when buffering is disabled. */
    private val offlineBuffer: Channel<BufferedEvent>? =
        if (bufferCapacity > 0) Channel(capacity = bufferCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST) else null

    init {
        // Single consumer: forward each queued event to the live transport, but only once one is bound
        // and flushing has been opened. While disconnected (or before init opens the gate) this loop
        // parks, so the buffer fills (drop-oldest) and drains in FIFO order once flushing opens.
        offlineBuffer?.let { buffer ->
            scope.launch {
                for (event in buffer) {
                    val transport = awaitFlushableTransport()
                    transport.sendRaw(event.messageType, event.payload)
                }
            }
        }
    }

    /** Suspends until a transport is bound and flushing has been opened, then returns that transport. */
    private suspend fun awaitFlushableTransport(): JetWhaleTransportMessenger = combine(live, flushOpen) { transport, open -> transport.takeIf { open } }
        .filterNotNull()
        .first()

    override fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean {
        val transport = live.value
        return when (policy) {
            // Queue always goes through the buffer so queued events keep one FIFO order, whether or
            // not a transport is currently bound (the consumer forwards them as soon as one is).
            OfflineSendPolicy.QUEUE -> {
                if (offlineBuffer == null) {
                    // No capacity: degrade to a best-effort live send.
                    if (transport != null) transport.sendRaw(messageType, payload) else false
                } else {
                    offlineBuffer.trySend(BufferedEvent(messageType, payload)) // drop-oldest never fails while open
                    true
                }
            }

            OfflineSendPolicy.DROP ->
                if (transport != null) transport.sendRaw(messageType, payload) else false

            OfflineSendPolicy.FAIL -> {
                if (transport == null) throw JetWhaleConnectionClosedException()
                when (transport.trySendRaw(messageType, payload)) {
                    RawSendOutcome.SENT -> true
                    RawSendOutcome.BUFFER_FULL -> false
                    RawSendOutcome.CONNECTION_CLOSED -> throw JetWhaleConnectionClosedException()
                }
            }
        }
    }

    override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String {
        // Requests are never buffered: a reply has nowhere to go while offline.
        val transport = live.value ?: throw JetWhaleConnectionClosedException()
        return transport.requestRaw(messageType, payload, timeout)
    }

    /**
     * Attaches the live transport. Requests and DROP/FAIL sends work immediately; buffered
     * [OfflineSendPolicy.QUEUE] events stay held until [startFlush] opens the gate.
     */
    public fun bind(transport: JetWhaleTransportMessenger) {
        live.value = transport
    }

    /** Opens flushing of buffered events. Call after connection-time initialization has finished. */
    public fun startFlush() {
        // Only meaningful while bound; a late call after unbind (e.g. init cancelled) is ignored.
        if (live.value != null) flushOpen.value = true
    }

    /** Detaches the live transport and re-closes the flush gate; QUEUE sends buffer again. */
    public fun unbind() {
        live.value = null
        flushOpen.value = false
    }

    /** Permanently stops this messenger. Call when the owning plugin is disposed. */
    public fun close() {
        offlineBuffer?.close()
        live.value = null
        flushOpen.value = false
        scope.cancel()
    }
}
