package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger
import com.kitakkun.jetwhale.protocol.messaging.SessionNegotiationScope

/**
 * Base class for a JetWhale plugin running inside the debug-target app (the agent).
 *
 * A plugin exchanges messages with its host counterpart through the symmetric [messenger]
 * (`trySend` / `sendOrQueue` / `sendOrFail` / `request`) and handles incoming messages
 * registered in [configure] (`onEvent<E>` / `onRequest`). Both ends use the same vocabulary; the
 * agent can `request` the host just as the host can `request` the agent.
 *
 * Unlike a host plugin, the **app** owns the instance — it constructs and registers it — so the
 * runtime does not create or dispose it, it **activates** and **deactivates** it:
 * [onActivate] (the host enabled this plugin) → [negotiation] / [onDisconnected] (each (re)connection
 * within the activation) → [onDeactivate] (the host disabled it). One-time setup goes in the
 * constructor; per-activation setup in [onActivate]; teardown of anything you started in
 * [onDeactivate].
 *
 * The [messenger] is **connection-independent**: it exists for the whole life of the plugin, not
 * just while a host is connected. Each event send states what to do offline — drop (`trySend`),
 * buffer and flush on reconnect (`sendOrQueue`), or throw (`sendOrFail`). Buffering is opt-in via
 * [offlineEventBufferCapacity].
 */
public abstract class JetWhaleAgentPlugin {
    /**
     * Unique id to distinguish plugins, e.g. "com.kitakkun.jetwhale.example".
     * Must match the host plugin's id so the two are paired.
     */
    public abstract val pluginId: String

    /** Version of this plugin, e.g. "1.0.0". */
    public abstract val pluginVersion: String

    /**
     * How many events `sendOrQueue` may hold while the host is disconnected, flushed in order on
     * reconnect. `0` (the default) disables buffering, so `sendOrQueue` then behaves like `trySend`.
     * The buffer is bounded and drops the **oldest** events when full.
     */
    protected open val offlineEventBufferCapacity: Int = 0

    private var boundMessenger: JetWhaleMessenger? = null

    /**
     * Sends messages to the host counterpart. Available from [onActivate] onwards (and inside handlers
     * and any code reached after it), for the whole life of the plugin — including while the host is
     * disconnected (see [offlineEventBufferCapacity]).
     */
    protected val messenger: JetWhaleMessenger
        get() = checkNotNull(boundMessenger) {
            "messenger is only available after the plugin has been registered (in or after onActivate())."
        }

    /** Registers handlers for messages from the host (`onEvent<E> { }` / `onRequest { req -> reply }`). */
    protected open fun JetWhaleMessagingHandlers.configure() {}

    /**
     * Called when the host **activates** this plugin (enables it), before any message flows and before
     * a connection is established. Per activation — it runs again if the host disables and re-enables
     * the plugin. Use it for local setup; for host communication use [negotiation]. Anything you start
     * here (e.g. a background job) should be stopped in [onDeactivate].
     */
    protected open fun onActivate() {}

    /**
     * The negotiation script this plugin runs on each (re)connection to exchange initial state with
     * the host. It runs over a [SessionNegotiationScope] (typed `send` / `receive`), and the plugin
     * operates only once it returns — buffered `sendOrQueue` events are held until then. By convention
     * the agent **initiates**: start with `send`, while the host's matching `negotiate` starts with
     * `receive`. Bounded by [negotiationTimeoutMillis]; on timeout the runtime warns and lets the
     * plugin proceed (degraded) rather than hang.
     */
    protected open suspend fun SessionNegotiationScope.negotiate() {}

    /** How long the runtime waits for [negotiate] before warning and proceeding. */
    protected open val negotiationTimeoutMillis: Long = 10_000

    /** Called when the connection drops (a transient disconnect; the plugin stays activated and keeps
     *  buffering for the next connection). Best-effort: the transport may already be gone. */
    protected open suspend fun onDisconnected() {}

    /**
     * Called when the host **deactivates** this plugin (disables it). Stop anything you started in
     * [onActivate] here — the runtime does not cancel the plugin's own jobs. Distinct from
     * [onDisconnected]: a disconnect is transient and keeps the plugin activated, whereas deactivation
     * means the host no longer wants the plugin running.
     */
    protected open fun onDeactivate() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

    /** The requested offline buffer capacity; the runtime reads this to size the [messenger]. */
    @InternalJetWhaleApi
    public fun offlineEventBufferCapacity(): Int = offlineEventBufferCapacity

    @InternalJetWhaleApi
    public fun registerHandlers(handlers: JetWhaleMessagingHandlers) {
        handlers.configure()
    }

    /** Binds the connection-independent messenger. Called once when the plugin is registered. */
    @InternalJetWhaleApi
    public fun bindMessenger(messenger: JetWhaleMessenger) {
        boundMessenger = messenger
    }

    @InternalJetWhaleApi
    public fun dispatchActivate() {
        onActivate()
    }

    @InternalJetWhaleApi
    public fun negotiationTimeoutMillis(): Long = negotiationTimeoutMillis

    @InternalJetWhaleApi
    public suspend fun runNegotiation(scope: SessionNegotiationScope) {
        scope.negotiate()
    }

    @InternalJetWhaleApi
    public suspend fun dispatchDisconnected() {
        onDisconnected()
    }

    @InternalJetWhaleApi
    public fun dispatchDeactivate() {
        onDeactivate()
    }
}
