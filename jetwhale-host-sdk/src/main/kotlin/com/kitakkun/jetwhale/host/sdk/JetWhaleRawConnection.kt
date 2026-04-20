package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope

/**
 * Represents an active connection between a host plugin and a debuggee session.
 * Provides event receiving and method dispatching capabilities.
 *
 * Obtain an instance via [JetWhaleMessagingCapablePlugin.onConnect].
 */
public interface JetWhaleRawConnection {
    /**
     * The CoroutineScope tied to the lifetime of this connection.
     */
    public val coroutineScope: CoroutineScope

    /**
     * Registers a handler to be called whenever a raw event string arrives from the debuggee.
     * Multiple handlers can be registered; they are called in registration order.
     *
     * @param handler Called with the raw event payload on the thread that delivers the event.
     */
    public fun receive(handler: (String) -> Unit)

    /**
     * Sends a raw method payload to the debuggee and suspends until the result is returned.
     *
     * @param method The raw encoded method payload.
     * @return The raw encoded result, or null if the call timed out or the debuggee returned no result.
     */
    public suspend fun send(method: String): String?
}
