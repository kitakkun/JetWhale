package com.kitakkun.jetwhale.host.mcp

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

    /**
     * Notify the service that a new plugin instance has been initialized for a session.
     * If the plugin implements [com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin],
     * its declared tools are registered and exposed via the MCP server.
     */
    fun onPluginInstanceReady(pluginId: String, sessionId: String)

    /**
     * Notify the service that a plugin instance has been disposed.
     * Any MCP tools previously registered for this (pluginId, sessionId) pair are removed.
     */
    fun onPluginInstanceDisposed(pluginId: String, sessionId: String)
}
