package com.kitakkun.jetwhale.protocol.messaging

/**
 * Marker for fire-and-forget messages. Only types implementing this can be passed to
 * [JetWhaleMessenger]'s `send`; handlers are registered with `onEvent<E>`.
 *
 * Events, [JetWhaleRequest]s, and replies partition the message space by marker:
 * a reply type implements neither marker and exists only as the result of a request.
 */
public interface JetWhaleEvent

/**
 * Marker for messages that expect a reply of type [R]. Only types implementing this can be passed
 * to [JetWhaleMessenger]'s `request`; handlers are registered with `onRequest` and must return [R].
 *
 * Declaring the reply type here is what lets both ends resolve it by type inference:
 * `request(GetConfig)` returns the `R` declared by `GetConfig : JetWhaleRequest<Config>`, and an
 * `onRequest { req: GetConfig -> ... }` handler must return that same `R` to compile.
 */
public interface JetWhaleRequest<R : Any>
