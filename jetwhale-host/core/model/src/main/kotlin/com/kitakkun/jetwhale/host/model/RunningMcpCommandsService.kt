package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long the UI keeps marking an AI agent's tool call. Most calls finish in tens of milliseconds,
 * faster than the UI samples [McpActivity], so a call marked only while it runs would often never
 * show.
 */
val AI_OPERATION_INDICATOR_LINGER: Duration = 1500.milliseconds

/**
 * Tells a plugin instance's UI which of its MCP commands an AI agent is running, so the plugin can
 * mark the control that does the same thing.
 */
interface RunningMcpCommandsService {
    /**
     * Which of [commandNames], the MCP commands of [pluginId]'s instance in [sessionId], an AI agent
     * is running now or finished less than [AI_OPERATION_INDICATOR_LINGER] ago. Emits again when a
     * finished command's hold runs out.
     */
    fun runningCommandNamesFlow(pluginId: String, sessionId: String, commandNames: Set<String>): Flow<Set<String>>
}
