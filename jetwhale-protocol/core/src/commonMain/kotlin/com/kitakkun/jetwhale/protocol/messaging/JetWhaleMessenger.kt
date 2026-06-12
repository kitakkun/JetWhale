package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer

/**
 * The sending face of a plugin connection. Symmetric: the host-side and agent-side messengers are
 * the same type with the same semantics.
 *
 * The interface itself is the *raw* (string) layer — the two shapes a transport needs. Plugin
 * authors use the typed [send] / [request] extensions, which capture the message serializer at the
 * call site via reified type parameters.
 */
public interface JetWhaleMessenger {
    /** Scope tied to this connection; cancelled when the connection closes. */
    public val coroutineScope: CoroutineScope

    /** Format used to (de)serialize message payloads. */
    public val payloadFormat: StringFormat

    /** Raw fire-and-forget shape. Prefer the typed [send] extension. */
    public fun sendRaw(messageType: String, payload: String)

    /**
     * Raw request-response shape. Prefer the typed [request] extension.
     * @throws JetWhaleRequestException on handler failure or timeout
     * @throws JetWhaleConnectionClosedException when the connection closes while waiting
     */
    public suspend fun requestRaw(messageType: String, payload: String): String
}

/**
 * Sends a fire-and-forget event. Only [JetWhaleEvent] types are accepted: passing a
 * [JetWhaleRequest] (or a reply type) is a compile-time error.
 */
public inline fun <reified E : JetWhaleEvent> JetWhaleMessenger.send(event: E) {
    val eventSerializer = serializer<E>()
    sendRaw(
        messageType = eventSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(eventSerializer, event),
    )
}

/**
 * Sends a request and suspends until its reply *value* arrives. The reply type [R] is resolved by
 * type inference from the request's `JetWhaleRequest<R>` declaration — no type argument, no cast:
 *
 * ```kotlin
 * val config: MockConfig = messenger.request(GetMockConfig) // GetMockConfig : JetWhaleRequest<MockConfig>
 * ```
 *
 * Use this when you consume the reply. To wait only for success and ignore the reply value (e.g. an
 * `Ack`), use [execute] — calling [request] and discarding its result leaves [R] unconstrained and
 * does not compile.
 *
 * @throws JetWhaleRequestException if the remote handler fails, none is registered, the reply
 *   cannot be decoded as [R], or the request times out
 * @throws JetWhaleConnectionClosedException when the connection closes while waiting
 */
public suspend inline fun <reified REQ : JetWhaleRequest<R>, reified R : Any> JetWhaleMessenger.request(
    request: REQ,
): R {
    val requestSerializer = serializer<REQ>()
    val replyPayload = requestRaw(
        messageType = requestSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(requestSerializer, request),
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

/**
 * Sends a request and suspends until the remote side has handled it, discarding the reply value.
 * Use this for requests whose reply is just an acknowledgement. Unlike [request], it does not reify
 * the reply type, so it is well-defined even though the reply is ignored.
 *
 * @throws JetWhaleRequestException if the remote handler fails, none is registered, or it times out
 * @throws JetWhaleConnectionClosedException when the connection closes while waiting
 */
public suspend inline fun <reified REQ : JetWhaleRequest<*>> JetWhaleMessenger.execute(request: REQ) {
    val requestSerializer = serializer<REQ>()
    requestRaw(
        messageType = requestSerializer.descriptor.serialName,
        payload = payloadFormat.encodeToString(requestSerializer, request),
    )
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
