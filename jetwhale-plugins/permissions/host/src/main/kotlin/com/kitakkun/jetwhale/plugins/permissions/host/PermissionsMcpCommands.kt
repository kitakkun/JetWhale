package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.plugins.permissions.protocol.PERMISSIONS_PLUGIN_ID
import kotlinx.serialization.json.Json

// Shared by the permissions plugin's MCP command classes (one class per file in this package).

// The tool names are namespaced by the pluginId, so they follow it rather than restating it.
internal const val TOOL_PREFIX = PERMISSIONS_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }
