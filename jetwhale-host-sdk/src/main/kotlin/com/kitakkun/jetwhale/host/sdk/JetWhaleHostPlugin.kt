package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol
import kotlinx.coroutines.CoroutineScope

/**
 * Typed base class for JetWhale host plugins.
 *
 * Extend this class to implement a plugin that:
 * - Receives typed [Event] objects from the debuggee.
 * - Dispatches typed [Method] calls to the debuggee and receives [MethodResult] back.
 * - Renders a Compose UI panel via [Content].
 *
 * The connection lifecycle is managed by the framework:
 * - [onConnect] is called (via [JetWhaleMessagingCapablePlugin]) when a session becomes active.
 * - [onDisconnect] is called when the session ends or the plugin is deactivated.
 *
 * Inside [Content] and MCP tool handlers, use [context] to dispatch methods to the debuggee.
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

    private var _context: JetWhaleDebugOperationContext<Method, MethodResult>? = null

    /**
     * The context for dispatching method calls to the debuggee.
     * Only accessible while the plugin is connected (between [onConnect] and [onDisconnect]).
     */
    protected val context: JetWhaleDebugOperationContext<Method, MethodResult>
        get() = checkNotNull(_context) { "Plugin is not connected to a session" }

    /**
     * Called when a typed event is received from the debuggee.
     *
     * @param event The decoded event.
     */
    public abstract fun onEvent(event: Event)

    /**
     * Composable UI panel for this plugin.
     *
     * Use [context] to dispatch method calls to the debuggee.
     * The composition is kept alive for the duration of the plugin instance.
     */
    @Composable
    public abstract override fun Content()

    final override fun onConnect(connection: JetWhaleRawConnection) {
        _context = object : JetWhaleDebugOperationContext<Method, MethodResult> {
            override val coroutineScope: CoroutineScope = connection.coroutineScope
            override suspend fun <MR : MethodResult> dispatch(method: Method): MR? {
                val encoded = protocol.encodeMethod(method)
                val raw = connection.send(encoded)
                @Suppress("UNCHECKED_CAST")
                return raw?.let { protocol.decodeMethodResult(it) } as? MR
            }
        }
        connection.receive { raw ->
            onEvent(protocol.decodeEvent(raw))
        }
    }

    final override fun onDisconnect() {
        _context = null
    }

    // Event routing now goes through JetWhaleMessagingCapablePlugin.onConnect / connection.receive.
    @Deprecated("Event routing is handled via JetWhaleMessagingCapablePlugin.onConnect.")
    final override fun onRawEvent(event: String): Unit = Unit

    @Deprecated("ContentRaw is replaced by Content() from JetWhaleRenderablePlugin.")
    @Composable
    final override fun ContentRaw(context: JetWhaleRawDebugOperationContext) {
        Content()
    }
}
