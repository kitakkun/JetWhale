package com.kitakkun.jetwhale.debugger.agent.runtime

import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi

/**
 * Plugin client which runs on the debug-target app.
 */
public interface JetWhaleAgentPlugin<Event> {
    /**
     * unique id to distinguish plugins.
     * For example, "com.kitakkun.jetwhale.debugger.agent.plugin.sample"
     */
    public val pluginId: String

    /**
     * Version of this plugin.
     * For example, "1.0.0"
     */
    public val pluginVersion: String

    /**
     * Handler to process method calls from the debugger.
     */
    @OptIn(InternalJetWhaleApi::class)
    public val methodHandler: MethodHandler

    /**
     * Dispatcher to send events to the debugger.
     */
    public val eventDispatcher: EventDispatcher<Event>

    /**
     * Enqueue an event to be sent to the debugger.
     * How the event is dispatched depends on the [eventDispatcher] implementation.
     * @see EventDispatcher for more details.
     */
    public fun enqueueEvent(event: Event) {
        eventDispatcher.dispatch(event)
    }
}

@OptIn(InternalJetWhaleApi::class)
public inline fun <reified Event> buildJetWhaleAgentPlugin(
    pluginId: String,
    pluginVersion: String,
    onReceiveMethod: MethodHandler,
    eventDispatcher: EventDispatcher<Event> = DropIfDisconnectedDispatcher(),
): JetWhaleAgentPlugin<Event> {
    return object : JetWhaleAgentPlugin<Event> {
        override val pluginId: String get() = pluginId
        override val pluginVersion: String get() = pluginVersion
        override val eventDispatcher: EventDispatcher<Event> = eventDispatcher
        override val methodHandler: MethodHandler = onReceiveMethod
    }
}

@OptIn(InternalJetWhaleApi::class)
public fun buildJetWhalePrimitiveAgentPlugin(
    pluginId: String,
    pluginVersion: String,
    onReceiveMethod: MethodHandler,
    eventDispatcher: EventDispatcher<String> = DropIfDisconnectedDispatcher(),
): JetWhaleAgentPlugin<String> {
    return buildJetWhaleAgentPlugin<String>(
        pluginId = pluginId,
        pluginVersion = pluginVersion,
        onReceiveMethod = onReceiveMethod,
        eventDispatcher = eventDispatcher,
    )
}
