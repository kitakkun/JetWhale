package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Typed handler registry, configured once per plugin instance (in `configure { ... }`).
 *
 * ```kotlin
 * onEvent { e: ButtonClicked -> counter++ }
 * onRequest { req: GetConfig -> reply(buildConfig()) }
 * ```
 *
 * Registration is reified: each call captures the serializer and the wire type key
 * (`descriptor.serialName`) of the concrete message type, so no sealed hierarchy or hand-written
 * protocol is needed. (A single overloaded `on` cannot be resolved from the lambda parameter type
 * alone — hence the two names.)
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
     * Registers a handler for the request type [REQ]. It must return — via [reply] — the reply type
     * declared by `REQ : JetWhaleRequest<R>`. The reply is sent when the handler returns, so
     * offload post-reply work to a scope instead of doing it before the final `reply(...)`.
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
