package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.STORAGE_PLUGIN_ID
import kotlinx.serialization.json.Json

internal const val TOOL_PREFIX = STORAGE_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }

internal const val PATH_ARGUMENT_DESCRIPTION = "Path below the root, with '/' between segments, e.g. 'files/datastore/settings.preferences_pb'."

/** A tool's `root` and `path` arguments as the location they name. */
internal fun fileLocationOf(rootName: String, path: String?): FileLocation = FileLocation(rootName = rootName, path = path.orEmpty().split('/').filter(String::isNotEmpty))
