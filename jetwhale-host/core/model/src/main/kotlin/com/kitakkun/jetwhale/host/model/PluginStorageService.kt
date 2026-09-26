package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage

/**
 * Hands each loaded plugin version its storage. A version that has no data yet starts from a copy of
 * the nearest older version's data, so an upgrade keeps the user's settings; the plugin's own
 * `storageVersion` migration then runs on that copy as it would on data it wrote itself.
 */
interface PluginStorageService {
    /**
     * The storage of [plugin]'s version, seeded as described above on its first use. The first use of
     * a version reads and writes files on the calling thread; later uses do not.
     */
    fun storageFor(plugin: LoadedHostPlugin): JetWhalePluginStorage
}
