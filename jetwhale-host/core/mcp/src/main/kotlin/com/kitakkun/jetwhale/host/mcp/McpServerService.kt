package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpServerStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Service that manages the MCP (Model Context Protocol) HTTP+SSE server embedded in JetWhale.
 *
 * The server exposes tools for AI agents to:
 * - List active debug sessions and plugins
 * - Capture screenshots of plugin ComposeScene frames
 * - Query accessibility/semantics trees of plugin UIs
 * - Dispatch click and keyboard events to plugin UIs
 * - Invoke plugin-defined semantic tools (via [com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin])
 */
interface McpServerService {
    val statusFlow: StateFlow<McpServerStatus>

    /**
     * Start the MCP HTTP+SSE server. Idempotent — calling while already running is a no-op.
     *
     * @param host Bind address. Defaults to "localhost".
     * @param port TCP port. Defaults to 7080.
     */
    suspend fun start(host: String = "localhost", port: Int = 7080)

    /**
     * Stop the MCP HTTP+SSE server. Idempotent — calling while already stopped is a no-op.
     */
    suspend fun stop()
}
