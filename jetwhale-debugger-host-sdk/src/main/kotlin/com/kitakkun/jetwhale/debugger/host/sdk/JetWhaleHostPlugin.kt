@file:OptIn(InternalJetWhaleHostApi::class)

package com.kitakkun.jetwhale.debugger.host.sdk

import androidx.compose.runtime.Composable
import kotlinx.serialization.serializer

/**
 * Plugin interface for JetWhale.
 *
 * Do not directly implement this interface. Use [buildJetWhaleHostPlugin] to create an instance.
 */
public interface JetWhaleHostPlugin {
    /**
     * Composable function that represents the UI of the plugin
     * @param context The context for building the UI
     */
    @Composable
    @InternalJetWhaleHostApi
    public fun Content(context: JetWhaleContentUIBuilderContext)

    /**
     * Called when an event is received from the JetWhale debugger
     * @param context The context containing the event data
     */
    @InternalJetWhaleHostApi
    public suspend fun onReceive(context: JetWhaleEventReceiverContext)

    /**
     * Called when the plugin is disposed
     */
    public fun onDispose() {
    }
}

public inline fun <reified Event, reified Method, reified MethodResult> buildJetWhaleHostPlugin(
    onReceiveEvent: EventReceiver<Event>,
    content: PluginUIBuilder<Method, MethodResult>,
): JetWhaleHostPlugin {
    return object : JetWhaleHostPlugin {
        override suspend fun onReceive(context: JetWhaleEventReceiverContext) {
            onReceiveEvent.receive(context.getDeserializedPayload(serializer()))
        }

        @Composable
        override fun Content(context: JetWhaleContentUIBuilderContext) {
            content.Content {
                context.dispatch(
                    methodSerializer = serializer(),
                    methodResultSerializer = serializer(),
                    value = it
                )
            }
        }
    }
}

public fun buildJetWhaleHostPlugin(
    content: @Composable () -> Unit,
): JetWhaleHostPlugin = buildJetWhaleHostPlugin<Unit, Unit, Unit>(
    onReceiveEvent = { },
    content = { content() }
)

public fun buildJetWhalePrimitiveHostPlugin(
    onReceiveEvent: EventReceiver<String>,
    content: PluginUIBuilder<String, String>,
): JetWhaleHostPlugin = buildJetWhaleHostPlugin(
    onReceiveEvent = onReceiveEvent,
    content = content,
)

public fun interface EventReceiver<Event> {
    public suspend fun receive(event: Event)
}

public fun interface PluginUIBuilder<Method, MethodResult> {
    @Composable
    public fun Content(methodDispatcher: MethodDispatcher<Method, MethodResult>)
}

public fun interface MethodDispatcher<Method, MethodResult> {
    public suspend fun dispatch(method: Method): MethodResult?
}
