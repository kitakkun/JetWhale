package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Typed handler registry, configured once per plugin instance (in `configure { ... }`).
 *
 * Whether a handler answers a request or consumes a fire-and-forget event is stated twice, on
 * purpose: by the message type ([JetWhaleEvent] vs [JetWhaleRequest]) and by the registration
 * function. A single overloaded `on` was tried and abandoned — with the message type only on the
 * lambda parameter, overload resolution cannot discriminate the two shapes (the two-type-variable
 * request bound is never pruned, and a member candidate "wins" by inferring an intersection type),
 * so the split names are what keeps registration checkable:
 *
 * ```kotlin
 * onEvent { e: ButtonClicked -> counter++ }                 // event: no reply
 * onRequest { req: GetConfig -> reply(buildConfig()) }      // request: must end with reply(...)
 * ```
 *
 * Registration is reified, which is what removes the need for a sealed message hierarchy and a
 * hand-written protocol: each registration captures the serializer and the wire type key
 * (`descriptor.serialName`) of the concrete message type. For requests, the reply type is inferred
 * from the request's [JetWhaleRequest] declaration, so a handler ending with a `reply(...)` of the
 * wrong type does not compile.
 */
public class JetWhaleMessageHandlers internal constructor() {
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
     * Registers a handler for the request type [REQ]. The handler must produce — via [reply] — the
     * reply type [R] declared by `REQ : JetWhaleRequest<R>`; anything else is a compile-time error.
     * One handler per type; the wrapped value is sent back as the reply once the handler returns
     * (offload post-reply work to a scope rather than doing it before the final `reply(...)`).
     */
    public inline fun <reified REQ : JetWhaleRequest<R>, reified R : Any> onRequest(
        noinline handler: suspend (REQ) -> Reply<R>,
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
        handler: suspend (REQ) -> Reply<R>,
    ) {
        val key = requestSerializer.descriptor.serialName
        check(key !in requestEntries) { "A request handler for '$key' is already registered." }
        @Suppress("UNCHECKED_CAST")
        requestEntries[key] = RequestEntry(requestSerializer, replySerializer) { value -> handler(value as REQ).value }
    }

    internal fun eventEntryFor(messageType: String): EventEntry? = eventEntries[messageType]

    internal fun requestEntryFor(messageType: String): RequestEntry? = requestEntries[messageType]
}
