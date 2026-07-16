package com.kitakkun.jetwhale.protocol.messaging

/**
 * Marker for fire-and-forget messages. Only types implementing this can be passed to
 * [JetWhaleMessenger]'s `send`; handlers are registered with `on { e: E -> ... }`.
 *
 * Events, [JetWhaleRequest]s, and reply values partition the message space by marker:
 * a reply type implements neither marker and exists only as the result of a request.
 */
public interface JetWhaleEvent

/**
 * Marker for messages that expect a reply of type [R]. Only types implementing this can be passed
 * to [JetWhaleMessenger]'s `request`; handlers are registered with `on { req: REQ -> ... reply(r) }`
 * and must return a [Reply] of [R].
 *
 * Declaring the reply type here is what lets both ends resolve it by type inference:
 * `request(GetConfig)` returns the `R` declared by `GetConfig : JetWhaleRequest<Config>`, and an
 * `on { req: GetConfig -> ... }` handler must end with `reply(config)` of that same `R` to compile.
 */
public interface JetWhaleRequest<R : Any>

/**
 * The value a request handler sends back over the wire, constructed only via [reply]. Making the
 * reply an explicit expression (rather than the handler's bare return value) keeps the handler's
 * kind visible at the registration site: an `on` handler that ends with `reply(...)` answers a
 * request; one that doesn't is a fire-and-forget event handler.
 */
public class Reply<R : Any> internal constructor(
    internal val value: R,
)

/** Wraps [value] as the reply to the request being handled. Only meaningful as the result of an `on` request handler. */
public fun <R : Any> reply(value: R): Reply<R> = Reply(value)
