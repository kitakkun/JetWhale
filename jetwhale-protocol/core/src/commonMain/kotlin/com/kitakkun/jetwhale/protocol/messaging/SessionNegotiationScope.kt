package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

/**
 * The exchange surface for a plugin's connection-time negotiation. Both the host and the agent run a
 * `negotiate { … }` script over this scope, sending and receiving typed messages in lock-step.
 *
 * Negotiation is **agent-initiated by convention**: the agent's script starts with [send] and the
 * host's with [receive]. Because the two scripts drive each other, a mismatch (both sides [receive],
 * or an unexpected message order) will stall — that is the plugin author's responsibility, but it is
 * never silent: [receive] of the wrong type throws, and the runtime bounds the whole negotiation with
 * a timeout that logs a warning and lets the plugin proceed (degraded) rather than hang forever.
 *
 * The interface is the *raw* (string) layer; authors use the typed [send] / [receive] extensions.
 */
public interface SessionNegotiationScope {
    /** Format used to (de)serialize negotiation message payloads. */
    public val payloadFormat: StringFormat

    /** Raw send. Prefer the typed [send] extension. */
    public suspend fun sendRaw(messageType: String, payload: String)

    /**
     * Raw receive: suspends until the next negotiation message arrives and returns its type
     * (serial name) and payload. Prefer the typed [receive] extension.
     */
    public suspend fun receiveRaw(): RawNegotiationMessage
}

/** A negotiation message in its on-the-wire form: the type's serial name and its serialized payload. */
public class RawNegotiationMessage(
    public val messageType: String,
    public val payload: String,
)

/** Sends [message] to the other side's negotiation script. */
public suspend inline fun <reified T : Any> SessionNegotiationScope.send(message: T) {
    val serializer = serializer<T>()
    sendRaw(serializer.descriptor.serialName, payloadFormat.encodeToString(serializer, message))
}

/**
 * Suspends until the next negotiation message arrives and decodes it as [T]. If the message is not of
 * type [T] (a protocol mismatch between the two scripts), throws [JetWhaleNegotiationException] rather
 * than misinterpreting it.
 */
public suspend inline fun <reified T : Any> SessionNegotiationScope.receive(): T {
    val serializer = serializer<T>()
    val raw = receiveRaw()
    if (raw.messageType != serializer.descriptor.serialName) {
        throw JetWhaleNegotiationException(
            "Negotiation expected '${serializer.descriptor.serialName}' but received '${raw.messageType}'.",
        )
    }
    return payloadFormat.decodeFromString(serializer, raw.payload)
}

/** A negotiation script failed: an unexpected message type, or the connection closed mid-negotiation. */
public class JetWhaleNegotiationException(
    message: String,
    cause: Throwable? = null,
) : JetWhaleMessagingException(message, cause)
