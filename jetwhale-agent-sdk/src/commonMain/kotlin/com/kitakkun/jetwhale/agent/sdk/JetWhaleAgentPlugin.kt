package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger

/**
 * Base class for a JetWhale plugin running inside the debug-target app (the agent).
 *
 * A plugin exchanges messages with its host counterpart through the symmetric [messenger]
 * (`send` / `request` / `execute`) and handles incoming messages registered in [configure]
 * (`onEvent<E>` / `onRequest`). Both ends use the same vocabulary; the agent can `request` the host
 * just as the host can `request` the agent.
 */
public abstract class JetWhaleAgentPlugin {
    /**
     * Unique id to distinguish plugins, e.g. "com.kitakkun.jetwhale.example".
     * Must match the host plugin's id so the two are paired.
     */
    public abstract val pluginId: String

    /** Version of this plugin, e.g. "1.0.0". */
    public abstract val pluginVersion: String

    private var boundMessenger: JetWhaleMessenger? = null

    /**
     * Sends messages to the host counterpart. Available from [onCreate] onwards (and inside handlers
     * and any code reached after `onCreate`). Notifications sent while disconnected are buffered and
     * flushed on reconnect.
     */
    protected val messenger: JetWhaleMessenger
        get() = checkNotNull(boundMessenger) {
            "messenger is only available after the plugin has been created (in or after onCreate())."
        }

    /**
     * The messenger if the plugin is currently connected, or null otherwise. Use this for events
     * fired by app code that may run while disconnected (e.g. captured network traffic); such
     * notifications are dropped while there is no connection rather than throwing.
     */
    protected val messengerOrNull: JetWhaleMessenger?
        get() = boundMessenger

    /** Registers handlers for messages from the host (`onEvent<E> { }` / `onRequest { req -> reply }`). */
    protected open fun JetWhaleMessagingHandlers.configure() {}

    /** Called once after [messenger] is bound and handlers are registered, before any message flows. */
    protected open fun onCreate() {}

    /** Called when the plugin is torn down (the agent runtime stops). */
    protected open fun onDispose() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

    @InternalJetWhaleApi
    public fun registerHandlers(handlers: JetWhaleMessagingHandlers) {
        handlers.configure()
    }

    @InternalJetWhaleApi
    public fun create(messenger: JetWhaleMessenger) {
        boundMessenger = messenger
        onCreate()
    }

    @InternalJetWhaleApi
    public fun dispose() {
        onDispose()
        boundMessenger = null
    }
}
