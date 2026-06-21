package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.SessionNegotiationScope

/**
 * Base class for a host plugin that exchanges messages with its agent counterpart (a
 * `JetWhaleAgentPlugin` with the **same `pluginId`**).
 *
 * It adds, on top of [JetWhaleHostPlugin]'s lifecycle, handler registration via [configure]
 * (`onEvent<E>` / `onRequest`) and [negotiate] — the host's half of the connection-time negotiation.
 * Combine with [JetWhaleHostPluginUi] to also render a UI.
 *
 * The host plugin does **not** hold a messenger: its lifetime is the session, so a stashed reference
 * would outlive the connection. Talk to the agent where the host hands you a session-scoped messenger
 * instead — [LocalJetWhaleMessenger] inside `Content()`, the `messenger` argument of an MCP tool, or
 * by replying from a handler / `negotiate`.
 */
public abstract class JetWhaleMessagingHostPlugin : JetWhaleHostPlugin() {
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
    public fun negotiationTimeoutMillis(): Long = negotiationTimeoutMillis

    @InternalJetWhaleHostApi
    public suspend fun runNegotiation(scope: SessionNegotiationScope) {
        scope.negotiate()
    }
}
