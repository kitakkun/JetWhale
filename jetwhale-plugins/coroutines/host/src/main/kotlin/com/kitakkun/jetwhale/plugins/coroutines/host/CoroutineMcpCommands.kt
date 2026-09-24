package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.COROUTINES_PLUGIN_ID
import kotlinx.serialization.json.Json

// Shared by the coroutine plugin's MCP command classes (one class per file in this package).

// The tool names are namespaced by the pluginId, so they follow it rather than restating it.
internal const val TOOL_PREFIX = COROUTINES_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }
