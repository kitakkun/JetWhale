package com.kitakkun.jetwhale.agent.sdk.messaging

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleConnectedMessenger
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleConnectionClosedException
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import kotlinx.serialization.serializer

/**
 * What a [send][sendOrQueue] should do when the connection is not currently available. Only events
 * (fire-and-forget) carry a policy; requests always fail when offline (see `request`).
 */
public enum class OfflineSendPolicy {
    /** Drop the event silently. The send reports `false` so the caller can react if it cares. */
    DROP,

    /**
     * Hold the event in a bounded offline buffer and flush it, in order, once the connection is
     * (re)established. Beyond a buffer with no capacity this degrades to [DROP].
     */
    QUEUE,

    /** Throw [JetWhaleConnectionClosedException] so the caller learns the event could not be sent. */
    FAIL,
}

/**
 * The **agent-side** sending face of a plugin connection. Extends the always-connected
 * [JetWhaleConnectedMessenger] with an offline-buffering vocabulary: the agent's messenger is
 * connection-independent (it outlives any single connection), so app code may send while
 * disconnected and choose what happens then via [OfflineSendPolicy].
 *
 * Host plugins do not see this type — they see the always-connected [JetWhaleConnectedMessenger],
 * where there is nothing to buffer across.
 *
 * The interface itself is the *raw* (string) layer. Plugin authors use the typed
 * `trySend` / [sendOrQueue] / [sendOrFail] / `request` extensions, which capture the message
 * serializer at the call site via reified type parameters.
 */
public interface JetWhaleMessenger : JetWhaleConnectedMessenger {
    /**
     * Raw fire-and-forget shape. Prefer the typed `trySend` / [sendOrQueue] / [sendOrFail]
     * extensions. [policy] decides what happens when the connection is unavailable.
     *
     * @return `true` if the event was sent or buffered, `false` if it was dropped.
     * @throws JetWhaleConnectionClosedException when [policy] is [OfflineSendPolicy.FAIL] and the
     *   connection is unavailable.
     */
    public fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean

    /** The always-connected send maps to a best-effort [OfflineSendPolicy.DROP]. */
    override fun sendRaw(messageType: String, payload: String): Boolean = sendRaw(messageType, payload, OfflineSendPolicy.DROP)
}

/**
 * Sends a fire-and-forget event, **buffering it** while the connection is unavailable and flushing
 * it (in order) on reconnect. Use this for streams you must not lose across a disconnect (e.g.
 * captured traffic). The buffer is bounded and opt-in — without capacity this behaves like
 * `trySend`. Buffering exists only on the agent's connection-independent messenger. Only
 * [JetWhaleEvent] types are accepted.
 */
public inline fun <reified E : JetWhaleEvent> JetWhaleMessenger.sendOrQueue(event: E) {
    val eventSerializer = serializer<E>()
    sendRaw(
        messageType = eventSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(eventSerializer, event),
        policy = OfflineSendPolicy.QUEUE,
    )
}

/**
 * Sends a fire-and-forget event, **throwing** [JetWhaleConnectionClosedException] if the connection
 * is unavailable. Use this when sending offline is a programming error you want surfaced. Only
 * [JetWhaleEvent] types are accepted.
 *
 * @throws JetWhaleConnectionClosedException when there is no live connection.
 */
public inline fun <reified E : JetWhaleEvent> JetWhaleMessenger.sendOrFail(event: E) {
    val eventSerializer = serializer<E>()
    sendRaw(
        messageType = eventSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(eventSerializer, event),
        policy = OfflineSendPolicy.FAIL,
    )
}
