package com.kitakkun.jetwhale.host.model

/**
 * What an MCP client needs to reach the host's server at [endpointUrl]: the command that registers it
 * with Claude Code, and the config block other clients take.
 */
@JvmInline
value class McpClientSetup(val endpointUrl: String) {
    val claudeCodeCommand: String get() = "claude mcp add --transport sse jetwhale $endpointUrl"

    val jsonConfig: String
        get() = """
            {
              "mcpServers": {
                "jetwhale": {
                  "type": "sse",
                  "url": "$endpointUrl"
                }
              }
            }
        """.trimIndent()

    companion object {
        const val GUIDE_URL: String = "${OfficialPlugin.DOCUMENTATION_URL}/guide/mcp-server"

        /** The setup for a server listening on [host]:[port]. */
        fun forServer(host: String, port: Int): McpClientSetup = McpClientSetup("http://$host:$port/sse")

        /** The setup for the server as it runs now, or a stopped one's configured [fallbackPort]. */
        fun of(status: McpServerStatus, fallbackPort: Int): McpClientSetup {
            val running = status as? McpServerStatus.Running
            return forServer(host = running?.host ?: "localhost", port = running?.port ?: fallbackPort)
        }
    }
}
