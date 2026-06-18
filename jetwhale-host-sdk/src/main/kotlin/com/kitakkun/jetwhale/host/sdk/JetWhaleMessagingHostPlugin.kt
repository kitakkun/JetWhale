package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger

/**
 * Base class for a host plugin that exchanges messages with its agent counterpart (a
 * `JetWhaleAgentPlugin` with the **same `pluginId`**).
 *
 * It adds, on top of [JetWhaleHostPlugin]'s lifecycle, the symmetric [messenger]
 * (`send` / `request` / `execute`) and handler registration via [configure] (`onEvent<E>` /
 * `onRequest`). The same vocabulary works in both directions. Combine with [JetWhaleHostPluginUi]
 * to also render a UI.
 */
public abstract class JetWhaleMessagingHostPlugin : JetWhaleHostPlugin() {
    private var boundMessenger: JetWhaleMessenger? = null

    /**
     * Sends messages to the agent counterpart. Available from [onCreate] onwards (and inside handlers
     * and the UI), for the lifetime of this plugin instance.
     */
    protected val messenger: JetWhaleMessenger
        get() = checkNotNull(boundMessenger) {
            "messenger is only available after the plugin instance has been created (in or after onCreate())."
        }

    /** Registers handlers for messages from the agent (`onEvent<E> { }` / `onRequest { req -> reply }`). */
    protected open fun JetWhaleMessagingHandlers.configure() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

    @InternalJetWhaleHostApi
    public fun registerHandlers(handlers: JetWhaleMessagingHandlers) {
        handlers.configure()
    }

    @InternalJetWhaleHostApi
    public fun bindMessenger(messenger: JetWhaleMessenger) {
        boundMessenger = messenger
    }
}
