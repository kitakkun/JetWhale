package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol

/**
 * Typed base class for JetWhale host plugins.
 *
 * Extend this class to implement a plugin that:
 * - Receives typed [Event] objects from the debuggee via [onEvent].
 * - Dispatches typed [Method] calls to the debuggee via [connection.send] and receives [MethodResult] back.
 * - Renders a Compose UI panel via [Content].
 *
 * The connection lifecycle is managed by the framework:
 * - [onConnect] (via [JetWhaleMessagingCapablePlugin]) is called when a session becomes active.
 * - [onDisconnect] is called when the session ends or the plugin is deactivated.
 *
 * Inside [Content] and MCP tool handlers, use [connection] to dispatch methods to the debuggee.
 *
 * @param Event        Type of events received from the debuggee.
 * @param Method       Type of method calls sent to the debuggee.
 * @param MethodResult Base type of method results returned by the debuggee.
 */
public abstract class JetWhaleHostPlugin<Event, Method, MethodResult> : JetWhaleRawHostPlugin(),
    JetWhaleRenderablePlugin,
    JetWhaleMessagingCapablePlugin {

    /**
     * The protocol used for encoding and decoding events, methods, and method results.
     */
    protected abstract val protocol: JetWhaleHostPluginProtocol<Event, Method, MethodResult>

    private var _connection: JetWhaleConnection<Event, Method, MethodResult>? = null

    /**
     * The typed connection to the debuggee session.
     * Only accessible while the plugin is connected (between [onConnect] and [onDisconnect]).
     *
     * Use [connection.send] to dispatch method calls and [connection.coroutineScope] for async work.
     */
    protected val connection: JetWhaleConnection<Event, Method, MethodResult>
        get() = checkNotNull(_connection) { "Plugin is not connected to a session" }

    /**
     * Called when a typed event is received from the debuggee.
     *
     * @param event The decoded event.
     */
    public abstract fun onEvent(event: Event)

    /**
     * Composable UI panel for this plugin.
     *
     * Use [connection] to dispatch method calls to the debuggee.
     * The composition is kept alive for the duration of the plugin instance.
     */
    @Composable
    public abstract override fun Content()

    final override fun onConnect(rawConnection: JetWhaleRawConnection) {
        val typed = object : JetWhaleConnection<Event, Method, MethodResult> {
            override val coroutineScope = rawConnection.coroutineScope
            override fun receive(handler: (Event) -> Unit) {
                rawConnection.receive { raw -> handler(protocol.decodeEvent(raw)) }
            }
            override suspend fun send(method: Method): MethodResult? {
                val raw = rawConnection.send(protocol.encodeMethod(method))
                return raw?.let { protocol.decodeMethodResult(it) }
            }
        }
        _connection = typed
        typed.receive { event -> onEvent(event) }
    }

    final override fun onDisconnect() {
        _connection = null
    }

    @Deprecated("Event routing is handled via JetWhaleMessagingCapablePlugin.onConnect.")
    final override fun onRawEvent(event: String): Unit = Unit

    @Deprecated("ContentRaw is replaced by Content() from JetWhaleRenderablePlugin.")
    @Composable
    final override fun ContentRaw(context: JetWhaleRawDebugOperationContext) {
        Content()
    }
}
