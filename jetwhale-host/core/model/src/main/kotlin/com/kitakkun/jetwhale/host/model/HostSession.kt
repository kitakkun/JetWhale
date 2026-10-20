package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest

/**
 * The session that plugins needing no app (`requiresAgent = false`) run in, and where a plugin that
 * needs an agent gets one more instance when it sets `runsInHostSession`.
 *
 * It is always there, is not a connection, and never closes, so its plugins are usable before any
 * app connects and outlive every app that comes and goes. No agent joins it, so its instances do no
 * messaging.
 */
object HostSession {
    /** Stable: MCP callers pass it as `sessionId` to reach these plugins. */
    const val ID: String = "host"

    /** Whether [sessionId] names this session rather than a connected app. */
    fun isHost(sessionId: String?): Boolean = sessionId == ID
}

/**
 * Whether the plugin this manifest declares gets an instance in [HostSession]: always when it needs
 * no agent, and next to its instances in app sessions when it sets `runsInHostSession`.
 */
val JetWhaleHostPluginManifest.targetsHostSession: Boolean
    get() = !requiresAgent || runsInHostSession
