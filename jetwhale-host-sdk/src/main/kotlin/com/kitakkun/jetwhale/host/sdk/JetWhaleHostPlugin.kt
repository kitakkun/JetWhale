package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol

/**
 * Base class for typed JetWhale host plugins: it decodes raw event strings into [Event]s via
 * [protocol] and hands them to [onEvent].
 *
 * This base is **headless** (no UI). For a plugin that renders a UI, extend [JetWhaleUiHostPlugin]
 * instead, which adds the [JetWhaleUiHostPlugin.Content] composable.
 */
public abstract class JetWhaleHostPlugin<Event, Method, MethodResult> : JetWhaleRawHostPlugin() {
    /**
     * The protocol used for encoding and decoding events, methods, and method results
     */
    protected abstract val protocol: JetWhaleHostPluginProtocol<Event, Method, MethodResult>

    /**
     * Called when an event is received from debuggee
     * @param event The received event
     */
    public abstract fun onEvent(event: Event)

    /**
     * Handles a raw event message received from the debuggee.
     * Decodes the message and processes it.
     */
    final override fun onRawEvent(event: String) {
        val decodedEvent = protocol.decodeEvent(event)
        onEvent(decodedEvent)
    }
}
