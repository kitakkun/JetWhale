package com.kitakkun.jetwhale.host.model

/**
 * The session that plugins needing no app (`requiresAgent = false`) run in.
 *
 * It is always there, is not a connection, and never closes, so those plugins are usable before any
 * app connects and outlive every app that comes and goes. Its plugins are instantiated here only, not
 * once per connected app.
 */
object HostSession {
    /** Stable: MCP callers pass it as `sessionId` to reach these plugins. */
    const val ID: String = "host"

    /** Whether [sessionId] names this session rather than a connected app. */
    fun isHost(sessionId: String?): Boolean = sessionId == ID
}
