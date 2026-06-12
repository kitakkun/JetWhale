package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Typed handler registry, configured once per plugin instance (in `configure { ... }`).
 *
 * Registration is reified, which is what removes the need for a sealed message hierarchy and a
 * hand-written protocol: each `onEvent`/`onRequest` call captures the serializer and the wire type
 * key (`descriptor.serialName`) of the concrete message type. For requests, the reply type is
 * inferred from the request's [JetWhaleRequest] declaration, so a handler returning the wrong
 * reply type does not compile.
 */
public class JetWhaleMessagingHandlers internal constructor() {
    internal class EventEntry(
        val serializer: KSerializer<*>,
        val handler: suspend (Any) -> Unit,
    )

    internal class RequestEntry(
        val requestSerializer: KSerializer<*>,
        val replySerializer: KSerializer<*>,
        val handler: suspend (Any) -> Any,
    )

    private val eventEntries = mutableMapOf<String, EventEntry>()
    private val requestEntries = mutableMapOf<String, RequestEntry>()

    /** Registers a handler for the event type [E]. One handler per type. */
    public inline fun <reified E : JetWhaleEvent> onEvent(noinline handler: suspend (E) -> Unit) {
        registerEvent(serializer<E>(), handler)
    }

    /**
     * Registers a handler for the request type [REQ]. The handler must return the reply type [R]
     * declared by `REQ : JetWhaleRequest<R>` — anything else is a compile-time error. One handler
     * per type; the returned value is sent back as the reply.
     */
    public inline fun <reified REQ : JetWhaleRequest<R>, reified R : Any> onRequest(
        noinline handler: suspend (REQ) -> R,
    ) {
        registerRequest(serializer<REQ>(), serializer<R>(), handler)
    }

    @PublishedApi
    internal fun <E : JetWhaleEvent> registerEvent(
        eventSerializer: KSerializer<E>,
        handler: suspend (E) -> Unit,
    ) {
        val key = eventSerializer.descriptor.serialName
        check(key !in eventEntries) { "An event handler for '$key' is already registered." }
        @Suppress("UNCHECKED_CAST")
        eventEntries[key] = EventEntry(eventSerializer) { value -> handler(value as E) }
    }

    @PublishedApi
    internal fun <REQ : JetWhaleRequest<R>, R : Any> registerRequest(
        requestSerializer: KSerializer<REQ>,
        replySerializer: KSerializer<R>,
        handler: suspend (REQ) -> R,
    ) {
        val key = requestSerializer.descriptor.serialName
        check(key !in requestEntries) { "A request handler for '$key' is already registered." }
        @Suppress("UNCHECKED_CAST")
        requestEntries[key] = RequestEntry(requestSerializer, replySerializer) { value -> handler(value as REQ) }
    }

    internal fun eventEntryFor(messageType: String): EventEntry? = eventEntries[messageType]

    internal fun requestEntryFor(messageType: String): RequestEntry? = requestEntries[messageType]
}
