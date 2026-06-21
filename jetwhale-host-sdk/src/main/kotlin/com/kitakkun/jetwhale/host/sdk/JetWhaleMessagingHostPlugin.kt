package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger
import com.kitakkun.jetwhale.protocol.messaging.SessionNegotiationScope

/**
 * Base class for a host plugin that exchanges messages with its agent counterpart (a
 * `JetWhaleAgentPlugin` with the **same `pluginId`**).
 *
 * It adds, on top of [JetWhaleHostPlugin]'s lifecycle, the symmetric [messenger]
 * (`trySend` / `sendOrFail` / `request`) and handler registration via [configure]
 * (`onEvent<E>` / `onRequest`), plus [negotiate] — the host's half of the connection-time
 * negotiation. The same vocabulary works in both directions. Combine with [JetWhaleHostPluginUi] to
 * also render a UI. (The host messenger is bound to a live session, so it has no offline buffer:
 * `sendOrQueue` degrades to a best-effort send here.)
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

    /**
     * The host's half of the connection-time negotiation, mirroring the agent's `negotiate`. Runs over
     * a [SessionNegotiationScope] (typed `send` / `receive`). By convention the agent **initiates**, so
     * this script starts with `receive`. Bounded by [negotiationTimeoutMillis]; on timeout the runtime
     * warns (visible in the host log) and proceeds.
     */
    protected open suspend fun SessionNegotiationScope.negotiate() {}

    /** How long the runtime waits for [negotiate] before warning and proceeding. */
    protected open val negotiationTimeoutMillis: Long = 10_000

    // -- runtime hooks (not for plugin authors) -------------------------------

    @InternalJetWhaleHostApi
    public fun registerHandlers(handlers: JetWhaleMessagingHandlers) {
        handlers.configure()
    }

    @InternalJetWhaleHostApi
    public fun bindMessenger(messenger: JetWhaleMessenger) {
        boundMessenger = messenger
    }

    @InternalJetWhaleHostApi
    public fun negotiationTimeoutMillis(): Long = negotiationTimeoutMillis

    @InternalJetWhaleHostApi
    public suspend fun runNegotiation(scope: SessionNegotiationScope) {
        scope.negotiate()
    }
}
