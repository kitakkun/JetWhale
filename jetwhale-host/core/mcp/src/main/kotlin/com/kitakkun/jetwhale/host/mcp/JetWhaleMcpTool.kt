package com.kitakkun.jetwhale.host.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface JetWhaleMcpTool {
    fun register(server: Server)
}
