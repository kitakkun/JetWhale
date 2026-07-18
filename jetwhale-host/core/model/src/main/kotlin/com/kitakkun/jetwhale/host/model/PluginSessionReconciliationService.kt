package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * The single owner of "which active sessions should hold an instance of which enabled plugin".
 *
 * It reconciles the enabled-plugin set against the active sessions, drives
 * [PluginInstanceService] to create/unload the instances, and emits the agent-facing
 * [PluginReconciliationEvent]s that the transport layer must forward. Any other component that needs
 * the target-session computation (e.g. hot reload) goes through [targetSessionIds] instead of
 * recomputing it, so the rule lives in exactly one place.
 */
interface PluginSessionReconciliationService {
    /**
     * Whether [pluginId] has an agent counterpart. A host-only plugin (requiresAgent = false) needs
     * no agent activation/deactivation. Unknown/unloaded plugins default to `true`.
     */
    fun requiresAgent(pluginId: String): Boolean

    /**
     * The ids of the sessions that should hold an instance of [pluginId] given [sessions]. An
     * agent-backed plugin targets only sessions whose agent advertised it; a host-only plugin
     * targets every session.
     */
    fun targetSessionIds(pluginId: String, sessions: List<DebugSession>): Set<String>

    /**
     * A cold flow that reconciles enabled plugins against active sessions for as long as it is
     * collected, initializing/unloading plugin instances as a side effect and emitting the
     * agent-facing notifications the collector must forward. Collect exactly once.
     */
    fun reconciliationEvents(): Flow<PluginReconciliationEvent>
}

/** An agent-facing notification produced while reconciling enabled plugins against active sessions. */
sealed interface PluginReconciliationEvent {
    /** [pluginId] was newly activated for [sessionIds]; notify each of those sessions' agents. */
    data class Activated(val pluginId: String, val sessionIds: Set<String>) : PluginReconciliationEvent

    /** [pluginId] was disabled; notify every connected agent. */
    data class Deactivated(val pluginId: String) : PluginReconciliationEvent
}
