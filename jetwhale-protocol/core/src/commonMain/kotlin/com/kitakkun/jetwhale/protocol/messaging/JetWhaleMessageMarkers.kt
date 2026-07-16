package com.kitakkun.jetwhale.protocol.messaging

/**
 * Marker for fire-and-forget messages. Only types implementing this can be passed to
 * [JetWhaleMessenger]'s `send`; handlers are registered with `onEvent`. A reply type implements
 * neither marker and exists only as the result of a request.
 */
public interface JetWhaleEvent

/**
 * Marker for messages that expect a reply of type [R]. Only types implementing this can be passed
 * to [JetWhaleMessenger]'s `request`; handlers are registered with `onRequest`. Declaring the reply
 * type here is what lets both ends resolve it by inference: `request(GetConfig)` returns the `R`
 * declared by `GetConfig : JetWhaleRequest<Config>`, and the handler must `reply` that same `R`.
 */
public interface JetWhaleRequest<R : Any>

/** The value a request handler sends back over the wire; constructed only via [reply]. */
public class Reply<R : Any> internal constructor(
    internal val value: R,
)

/** Wraps [value] as the reply to the request being handled. */
public fun <R : Any> reply(value: R): Reply<R> = Reply(value)
