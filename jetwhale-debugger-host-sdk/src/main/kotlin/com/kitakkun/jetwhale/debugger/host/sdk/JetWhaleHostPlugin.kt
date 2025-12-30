package com.kitakkun.jetwhale.debugger.host.sdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.serialization.JetWhaleJson
import kotlinx.serialization.serializer

/**
 * Plugin interface for JetWhale
 */
public interface JetWhaleHostPlugin {
    public suspend fun onReceive(payload: String)

    public fun onDispose() {}

    /**
     * Composable function that represents the UI of the plugin
     */
    @Composable
    public fun Content(methodDispatcher: MethodDispatcher<String, String>)
}

@OptIn(InternalJetWhaleApi::class)
public inline fun <reified Event, reified Method, reified MethodResult> buildJetWhaleHostPlugin(
    onReceiveEvent: EventReceiver<Event>,
    content: PluginUIBuilder<Method, MethodResult>,
): JetWhaleHostPlugin {
    return object : JetWhaleHostPlugin {
        override suspend fun onReceive(payload: String) {
            onReceiveEvent.receive(JetWhaleJson.decodeFromString(serializer<Event>(), payload))
        }

        @Composable
        override fun Content(methodDispatcher: MethodDispatcher<String, String>) {
            val methodDispatcher = remember(methodDispatcher) {
                MethodDispatcher { method: Method ->
                    val serializedMethod = JetWhaleJson.encodeToString(serializer<Method>(), method)
                    methodDispatcher.dispatch(serializedMethod)?.let {
                        JetWhaleJson.decodeFromString(serializer<MethodResult>(), it)
                    }
                }
            }
            content.Content(methodDispatcher)
        }
    }
}

@OptIn(InternalJetWhaleApi::class)
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
