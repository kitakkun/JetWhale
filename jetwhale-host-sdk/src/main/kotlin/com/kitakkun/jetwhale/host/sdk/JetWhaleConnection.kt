package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope

/**
 * Represents an active connection between a host plugin and a debuggee session.
 *
 * @param Event        Type of events received from the debuggee.
 * @param Method       Type of method calls sent to the debuggee.
 * @param MethodResult Type of results returned by the debuggee for method calls.
 */
public interface JetWhaleConnection<Event, Method, MethodResult> {
    /**
     * The CoroutineScope tied to the lifetime of this connection.
     */
    public val coroutineScope: CoroutineScope

    /**
     * Registers a handler to be called whenever an event arrives from the debuggee.
     * Multiple handlers can be registered; they are called in registration order.
     *
     * @param handler Called with the decoded event on the thread that delivers the event.
     */
    public fun receive(handler: (Event) -> Unit)

    /**
     * Sends a method call to the debuggee and suspends until the result is returned.
     *
     * @param method The method to dispatch.
     * @return The result, or null if the call timed out or the debuggee returned no result.
     */
    public suspend fun send(method: Method): MethodResult?
}

/**
 * Raw string-based connection. Used by [JetWhaleMessagingCapablePlugin] as the common interface
 * for all plugin types, regardless of their encoding protocol.
 */
public typealias JetWhaleRawConnection = JetWhaleConnection<String, String, String>
