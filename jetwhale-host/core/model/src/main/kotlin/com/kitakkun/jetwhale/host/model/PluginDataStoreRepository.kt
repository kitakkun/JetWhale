package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage

/**
 * Provides per-plugin persistent storage. The single data source is the on-disk plugin data
 * directory; this repository owns reading/writing it and nothing else.
 *
 * Security: a plugin only ever receives a [JetWhalePluginStorage] scoped to its own `pluginId`
 * (see [storageFor]), and each plugin's data lives in a separate directory, so one plugin cannot
 * read or write another plugin's data.
 */
interface PluginDataStoreRepository {
    /**
     * Returns the [JetWhalePluginStorage] handle for [pluginId]. The same handle is returned for
     * the same id, so all sessions of one plugin share its persistent data.
     */
    fun storageFor(pluginId: String): JetWhalePluginStorage
}
