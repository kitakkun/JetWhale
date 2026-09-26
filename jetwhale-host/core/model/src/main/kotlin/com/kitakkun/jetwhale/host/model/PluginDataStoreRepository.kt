package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import kotlinx.serialization.json.JsonElement

/**
 * Provides per-plugin, per-version persistent storage. The single data source is the on-disk plugin
 * data directory; this repository owns reading/writing it and nothing else.
 *
 * Security: a plugin only ever receives a [JetWhalePluginStorage] scoped to its own `pluginId` and
 * version (see [storageFor]), and each lives in a separate directory, so one plugin cannot read or
 * write another plugin's data. Two versions of one plugin that run at once never share a file.
 */
interface PluginDataStoreRepository {
    /**
     * Returns the [JetWhalePluginStorage] handle for [version] of [pluginId]. The same handle is
     * returned for the same pair, so all sessions bound to one version share its persistent data.
     */
    fun storageFor(pluginId: String, version: String): JetWhalePluginStorage

    /**
     * The versions of [pluginId] that have stored data on disk, oldest first. Null stands for data
     * written before storage was kept per version, which counts as older than every version.
     */
    fun storedVersions(pluginId: String): List<String?>

    /** Reads every stored key of [version] of [pluginId] (null: the unversioned data) as its JSON value. */
    fun readEntries(pluginId: String, version: String?): Map<String, JsonElement>

    /**
     * Writes [entries] as the initial data of [version] of [pluginId]. Only for a version with no data
     * yet, before its [storageFor] handle is first used.
     */
    fun seed(pluginId: String, version: String, entries: Map<String, JsonElement>)
}
