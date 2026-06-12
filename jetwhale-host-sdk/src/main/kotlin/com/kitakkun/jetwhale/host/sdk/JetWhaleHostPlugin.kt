package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger

/**
 * Base class for a JetWhale host plugin (the debugger-side counterpart of a `JetWhaleAgentPlugin`).
 *
 * A plugin exchanges messages with its agent counterpart through the symmetric [messenger]
 * (`send` / `request` / `execute`) and handles incoming messages registered in [configure]
 * (`onEvent<E>` / `onRequest`). The same vocabulary works in both directions.
 *
 * This base is **headless** (no UI). A plugin that renders a UI additionally implements
 * [JetWhaleHostPluginUi].
 */
public abstract class JetWhaleHostPlugin {
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

    /** Called once after [messenger] is bound and handlers are registered, before any message flows. */
    protected open fun onCreate() {}

    /** Called when this plugin instance is disposed (session closed, plugin disabled, or reloaded). */
    public open fun onDispose() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

    @InternalJetWhaleHostApi
    public fun registerHandlers(handlers: JetWhaleMessagingHandlers) {
        handlers.configure()
    }

    @InternalJetWhaleHostApi
    public fun create(messenger: JetWhaleMessenger) {
        boundMessenger = messenger
        onCreate()
    }

    @InternalJetWhaleHostApi
    public fun dispose() {
        onDispose()
        boundMessenger = null
    }
}

/**
 * Marks host-runtime-only entry points that plugin authors must not call. The host wires these when
 * it creates a plugin instance.
 */
@RequiresOptIn(message = "This is a JetWhale host-runtime API and must not be called by plugin code.")
@Retention(AnnotationRetention.BINARY)
public annotation class InternalJetWhaleHostApi
