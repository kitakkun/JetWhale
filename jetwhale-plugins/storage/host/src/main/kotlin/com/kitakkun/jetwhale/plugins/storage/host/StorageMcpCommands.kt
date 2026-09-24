package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.STORAGE_PLUGIN_ID
import kotlinx.serialization.json.Json

// Shared by the storage plugin's MCP command classes (one class per file in this package).

// The tool names are namespaced by the pluginId, so they follow it rather than restating it.
internal const val TOOL_PREFIX = STORAGE_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }

internal const val PATH_ARGUMENT_DESCRIPTION = "Path below the root, with '/' between segments, e.g. 'files/datastore/settings.preferences_pb'."

/** A tool's `root` and `path` arguments as the location they name. */
internal fun fileLocationOf(rootName: String, path: String?): FileLocation =
    FileLocation(rootName = rootName, path = path.orEmpty().split('/').filter(String::isNotEmpty))
