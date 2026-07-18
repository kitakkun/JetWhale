package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlin.time.Duration

/**
 * The always-connected sending face of a plugin connection: fire-and-forget events and
 * request-reply, with **no offline-buffering vocabulary**.
 *
 * This is the face a **host** plugin sees. A host plugin instance lives exactly as long as its
 * connection's session, so it is always connected for the whole of its lifetime — there is nothing
 * to buffer *across* and no send policy to choose. Offline buffering is an agent-side concept only;
 * the agent's connection-independent messenger (`JetWhaleMessenger`, defined on the agent side)
 * layers an offline send policy and `sendOrQueue` / `sendOrFail` on top of this base.
 *
 * The interface itself is the *raw* (string) layer — the two shapes a transport needs. Plugin
 * authors use the typed [trySend] / [request] extensions, which capture the message serializer at
 * the call site via reified type parameters.
 */
public interface JetWhaleConnectedMessenger {
    /** Format used to (de)serialize message payloads. */
    public val payloadFormat: StringFormat

    /**
     * Raw fire-and-forget shape. Prefer the typed [trySend] extension. Hands the event to the live
     * connection.
     *
     * @return `true` if the event was handed to the connection, `false` if it could not be (e.g. the
     *   connection is closing).
     */
    public fun sendRaw(messageType: String, payload: String): Boolean

    /**
     * Raw request-response shape. Prefer the typed [request] extension. [timeout] overrides the
     * connection's default request timeout for this call; `null` uses the default.
     * @throws JetWhaleRequestException on handler failure or timeout
     * @throws JetWhaleConnectionClosedException when the connection closes while waiting
     */
    public suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String
}

/**
 * Sends a fire-and-forget event to the connection. Only [JetWhaleEvent] types are accepted: passing
 * a [JetWhaleRequest] is a compile-time error.
 *
 * On the always-connected host face this hands the event to the live connection. On the agent's
 * `JetWhaleMessenger` the same call **drops** the event if the connection is currently unavailable
 * (use `sendOrQueue` / `sendOrFail` there to choose otherwise).
 *
 * @return `true` if the event was handed to the connection, `false` if there was none.
 */
public inline fun <reified E : JetWhaleEvent> JetWhaleConnectedMessenger.trySend(event: E): Boolean {
    val eventSerializer = serializer<E>()
    return sendRaw(
        messageType = eventSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(eventSerializer, event),
    )
}

/**
 * Sends a request and suspends until its reply *value* arrives. The reply type [R] is resolved by
 * type inference from the request's `JetWhaleRequest<R>` declaration — no type argument, no cast:
 *
 * ```kotlin
 * val pong: Pong = messenger.request(Ping) // Ping : JetWhaleRequest<Pong>
 * ```
 *
 * If you only need the call to succeed (e.g. a command whose reply is just an `Ack`), simply
 * discard the result — `R` is still inferred from the request's declaration.
 *
 * Pass [timeout] to override the connection's default request timeout for this call.
 *
 * @throws JetWhaleRequestException if the remote handler fails, none is registered, the reply
 *   cannot be decoded as [R], or the request times out
 * @throws JetWhaleConnectionClosedException when offline, or when the connection closes while waiting
 */
public suspend inline fun <reified REQ : JetWhaleRequest<R>, reified R : Any> JetWhaleConnectedMessenger.request(
    request: REQ,
    timeout: Duration? = null,
): R {
    val requestSerializer = serializer<REQ>()
    val replyPayload = requestRaw(
        messageType = requestSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(requestSerializer, request),
        timeout = timeout,
    )
    return try {
        payloadFormat.decodeFromString(serializer<R>(), replyPayload)
    } catch (e: kotlinx.serialization.SerializationException) {
        throw JetWhaleRequestException(
            "Reply to '${requestSerializer.descriptor.serialName}' could not be decoded as the declared reply type: ${e.message}",
            e,
        )
    }
}
