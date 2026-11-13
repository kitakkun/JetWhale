package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger

/**
 * Base class for a host plugin that exchanges messages with its agent counterpart (a
 * `JetWhaleAgentPlugin` with the **same `pluginId`**).
 *
 * An instance in an app session lives exactly as long as its connection's session: while the
 * instance is alive there is a live counterpart, so [messenger] is a plain property, valid from
 * [onCreate] through [onDispose], and may be held anywhere — UI callbacks, MCP tool handlers,
 * background jobs on [pluginScope]. State that must survive across sessions does not belong in the
 * instance.
 *
 * Lifecycle in an app session: `configure` (handler registration) → [onCreate] (sync, local setup) →
 * [onPrepare] (suspend, initial exchange; no handler runs until it returns) → handlers/UI/MCP →
 * [onDispose]. Combine with [JetWhaleHostPluginUi] to also render a UI.
 *
 * A plugin whose manifest sets `"runsInHostSession": true` also has an instance in the host session,
 * which has no agent. That instance is told so by [isInHostSession]: it gets no `configure` and no
 * [onPrepare], only [onCreate] → UI/MCP → [onDispose], and its [messenger] throws.
 */
public abstract class JetWhaleMessagingHostPlugin : JetWhaleHostPlugin() {
    private var boundMessenger: JetWhaleMessenger? = null

    /**
     * Sends messages to the agent counterpart: `trySend(event)` for fire-and-forget events,
     * `request(req): R` for request-reply. Valid for the whole life of an instance in an app session
     * ([onCreate] onwards) — the instance only exists while its session is connected, so the
     * messenger is **always connected** for the instance's lifetime. There is no offline-buffering
     * vocabulary here (no `sendOrQueue` / `sendOrFail` / send policy): offline buffering is an
     * agent-side concept, and a host plugin has nothing to buffer across.
     *
     * @throws IllegalStateException in the host session ([isInHostSession]), which has no agent.
     */
    protected val messenger: JetWhaleMessenger
        get() {
            boundMessenger?.let { return it }
            check(boundIsInHostSession != true) {
                "messenger is not available in the host session, which has no agent. Check isInHostSession before using it."
            }
            error("messenger is only available after the plugin instance has been bound (in or after onCreate()).")
        }

    /**
     * Registers handlers for messages from the agent. `onEvent { e: E -> ... }` for
     * events, `onRequest { req: REQ -> ... reply(r) }` for requests.
     */
    protected open fun JetWhaleMessageHandlers.configure() {}

    /**
     * The plugin's initial exchange with the agent, run once after [onCreate] and before any
     * handler is dispatched — inbound events and requests are held (in arrival order) until this
     * returns, so handlers never observe un-prepared state. Use plain [messenger] calls, typically
     * `request`:
     *
     * ```kotlin
     * override suspend fun onPrepare() {
     *     val config = messenger.request(GetMockConfig)
     *     applyConfig(config)
     * }
     * ```
     *
     * Bounded by [prepareTimeoutMillis]: on timeout the runtime warns (visible in the host log) and
     * opens handler dispatch anyway, so a hung preparation degrades instead of freezing the plugin.
     * By convention only one side of a plugin pair actively `request`s during preparation (the other
     * side answers from its handlers).
     */
    protected open suspend fun onPrepare() {}

    /** How long the runtime waits for [onPrepare] before warning and proceeding. */
    protected open val prepareTimeoutMillis: Long = 10_000

    @InternalJetWhaleHostApi
    public fun registerHandlers(handlers: JetWhaleMessageHandlers) {
        handlers.configure()
    }

    /** Binds the session-scoped messenger of an app-session instance. Called once, before [onCreate]. */
    @InternalJetWhaleHostApi
    public fun bindMessenger(messenger: JetWhaleMessenger) {
        boundMessenger = messenger
    }

    @InternalJetWhaleHostApi
    public fun prepareTimeoutMillis(): Long = prepareTimeoutMillis

    @InternalJetWhaleHostApi
    public suspend fun dispatchPrepare() {
        onPrepare()
    }
}
