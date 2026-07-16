package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger

/**
 * Base class for a JetWhale plugin running inside the debug-target app (the agent).
 *
 * A plugin exchanges messages with its host counterpart through the symmetric [messenger]
 * (`trySend` / `sendOrQueue` / `sendOrFail` / `request`) and handles incoming messages
 * registered in [configure] (`onEvent { e: E -> }` / `onRequest { req -> reply(...) }`). Both ends use the same
 * vocabulary; the agent can `request` the host just as the host can `request` the agent.
 *
 * Unlike a host plugin, the **app** owns the instance — it constructs and registers it — so the
 * runtime does not create or dispose it, it **activates** and **deactivates** it:
 * [onActivate] (the host enabled this plugin) → [onPrepare] / [onDisconnected] (each (re)connection
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
            "messenger is not bound: the plugin has not been registered with the JetWhale agent runtime."
        }

    /** Registers handlers for messages from the host (`onEvent { e: E -> }` / `onRequest { req -> reply(...) }`). */
    protected open fun JetWhaleMessageHandlers.configure() {}

    /**
     * Called when the host **activates** this plugin (enables it), before any message flows and before
     * a connection is established. Per activation — it runs again if the host disables and re-enables
     * the plugin. Use it for local setup; for host communication use [onPrepare]. Anything you start
     * here (e.g. a background job) should be stopped in [onDeactivate].
     */
    protected open fun onActivate() {}

    /**
     * The plugin's initial exchange with the host, run on each (re)connection. Until it returns, no
     * handler registered in [configure] is dispatched and buffered `sendOrQueue` events are held —
     * so handlers and the host never observe un-prepared state. Use plain [messenger] calls,
     * typically `request`. By convention only one side of a plugin pair actively `request`s during
     * preparation (the other side answers from its handlers). Bounded by [prepareTimeoutMillis]; on
     * timeout the runtime warns and lets the plugin proceed (degraded) rather than hang.
     */
    protected open suspend fun onPrepare() {}

    /** How long the runtime waits for [onPrepare] before warning and proceeding. */
    protected open val prepareTimeoutMillis: Long = 10_000

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
    public fun registerHandlers(handlers: JetWhaleMessageHandlers) {
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
    public fun prepareTimeoutMillis(): Long = prepareTimeoutMillis

    @InternalJetWhaleApi
    public suspend fun dispatchPrepare() {
        onPrepare()
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
