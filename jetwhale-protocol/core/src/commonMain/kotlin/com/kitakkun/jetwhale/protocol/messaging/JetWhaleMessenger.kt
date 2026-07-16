package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlin.time.Duration

/**
 * What a [send][trySend] should do when the connection is not currently available. Only events
 * (fire-and-forget) carry a policy; requests always fail when offline (see [request]).
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
 * The sending face of a plugin connection. Symmetric: the host-side and agent-side messengers are
 * the same type with the same semantics.
 *
 * The interface itself is the *raw* (string) layer — the two shapes a transport needs. Plugin
 * authors use the typed [trySend] / [sendOrQueue] / [sendOrFail] / [request] extensions, which
 * capture the message serializer at the call site via reified type parameters.
 */
public interface JetWhaleMessenger {
    /** Scope tied to this connection; cancelled when the connection closes. */
    public val coroutineScope: CoroutineScope

    /** Format used to (de)serialize message payloads. */
    public val payloadFormat: StringFormat

    /**
     * Raw fire-and-forget shape. Prefer the typed [trySend] / [sendOrQueue] / [sendOrFail]
     * extensions. [policy] decides what happens when the connection is unavailable.
     *
     * @return `true` if the event was sent or buffered, `false` if it was dropped.
     * @throws JetWhaleConnectionClosedException when [policy] is [OfflineSendPolicy.FAIL] and the
     *   connection is unavailable.
     */
    public fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean

    /**
     * Raw request-response shape. Prefer the typed [request] extension. [timeout] overrides the
     * connection's default request timeout for this call; `null` uses the default.
     * @throws JetWhaleRequestException on handler failure or timeout
     * @throws JetWhaleConnectionClosedException when offline, or when the connection closes while waiting
     */
    public suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String
}

/**
 * Sends a fire-and-forget event if the connection is available, otherwise **drops it** and returns
 * `false`. Use this for events that are only meaningful live (a momentary signal). Only
 * [JetWhaleEvent] types are accepted: passing a [JetWhaleRequest] is a compile-time error.
 *
 * @return `true` if the event was handed to the live connection, `false` if there was none.
 */
public inline fun <reified E : JetWhaleEvent> JetWhaleMessenger.trySend(event: E): Boolean {
    val eventSerializer = serializer<E>()
    return sendRaw(
        messageType = eventSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(eventSerializer, event),
        policy = OfflineSendPolicy.DROP,
    )
}

/**
 * Sends a fire-and-forget event, **buffering it** while the connection is unavailable and flushing
 * it (in order) on reconnect. Use this for streams you must not lose across a disconnect (e.g.
 * captured traffic). The buffer is bounded and opt-in — without capacity this behaves like
 * [trySend]. Only [JetWhaleEvent] types are accepted.
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
public suspend inline fun <reified REQ : JetWhaleRequest<R>, reified R : Any> JetWhaleMessenger.request(
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

/** Base type for messaging failures. */
public open class JetWhaleMessagingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A request failed: remote handler error, no handler registered, undecodable reply, or timeout. */
public class JetWhaleRequestException(
    message: String,
    cause: Throwable? = null,
) : JetWhaleMessagingException(message, cause)

/** The connection closed while a request was waiting for its reply. */
public class JetWhaleConnectionClosedException(
    message: String = "The connection was closed",
) : JetWhaleMessagingException(message)
