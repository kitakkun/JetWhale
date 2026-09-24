package com.kitakkun.jetwhale.plugins.screen.host

import com.kitakkun.jetwhale.plugins.screen.protocol.SCREEN_PLUGIN_ID

// The tool names are namespaced by the pluginId, so they follow it rather than restating it.
internal const val TOOL_PREFIX = SCREEN_PLUGIN_ID

/** What the MCP commands drive; the host plugin is the only implementation. Each call returns the status line. */
internal interface ScreenStreamController {
    suspend fun start(scale: Float, jpegQuality: Int, maxFramesPerSecond: Int): String

    suspend fun stop(): String

    fun stats(): StreamStatsSnapshot
}
