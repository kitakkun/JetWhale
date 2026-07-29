package com.kitakkun.jetwhale.host.sdk.web

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Delivers agent-originated messages from the plugin (Kotlin) into the web UI (JavaScript).
 *
 * The web UI reaches the agent through the `window.jetwhale` bridge that [JetWhaleWebView] injects:
 * - `window.jetwhale.send(type, payloadJson)` and `window.jetwhale.request(type, payloadJson)` go
 *   straight to the agent through the plugin's messenger — no Kotlin needed per message.
 * - The other direction (agent → web UI) goes through this bridge: register a typed handler in
 *   `configure { ... }` and forward it with [emit]. [JetWhaleWebView] passes it to every
 *   `window.jetwhale.onMessage` listener as `(type, payloadJson)`.
 *
 * ```kotlin
 * private val bridge = JetWhaleWebBridge()
 *
 * override fun JetWhaleMessageHandlers.configure() {
 *     onEvent { e: ButtonClicked -> bridge.emit("ButtonClicked", Json.encodeToString(e)) }
 * }
 * ```
 */
@ExperimentalJetWhaleApi
public class JetWhaleWebBridge {
    private val inboundFlow = MutableSharedFlow<InboundMessage>(extraBufferCapacity = 64)

    internal val inbound: Flow<InboundMessage> = inboundFlow

    /**
     * Delivers a message to the web UI's `window.jetwhale.onMessage(type, payload)` listeners.
     *
     * [payload] must be a JSON string, or `""` when there is none. Callable from any thread. The
     * message is dropped when no [JetWhaleWebView] is currently mounted to receive it — inbound
     * messages are not buffered across mounts.
     */
    public fun emit(messageType: String, payload: String) {
        inboundFlow.tryEmit(InboundMessage(messageType, payload))
    }

    internal data class InboundMessage(val messageType: String, val payload: String)
}
