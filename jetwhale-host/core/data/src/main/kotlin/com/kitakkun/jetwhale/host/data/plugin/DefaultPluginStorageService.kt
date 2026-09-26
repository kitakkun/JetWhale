package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.model.PluginVersionOrder
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginStorageService(
    private val pluginDataStoreRepository: PluginDataStoreRepository,
) : PluginStorageService {
    private val seededVersions: MutableSet<Pair<String, String>> = ConcurrentHashMap.newKeySet()
    private val seedLock = Any()

    override fun storageFor(plugin: LoadedHostPlugin): JetWhalePluginStorage {
        val pluginId = plugin.manifest.pluginId
        val version = plugin.manifest.version
        if (pluginId to version !in seededVersions) {
            synchronized(seedLock) {
                if (seededVersions.add(pluginId to version)) seedIfNew(pluginId, version)
            }
        }
        return pluginDataStoreRepository.storageFor(pluginId, version)
    }

    private fun seedIfNew(pluginId: String, version: String) {
        val storedVersions = pluginDataStoreRepository.storedVersions(pluginId)
        if (version in storedVersions) return
        // Null, the unversioned data, is older than every version.
        val olderVersions = storedVersions.filter { stored -> stored == null || PluginVersionOrder.compare(stored, version) < 0 }
        if (olderVersions.isEmpty()) return
        val sourceVersion = olderVersions.last()
        pluginDataStoreRepository.seed(pluginId, version, pluginDataStoreRepository.readEntries(pluginId, sourceVersion))
        logger.info("Plugin '$pluginId' $version starts from a copy of the data of ${sourceVersion ?: "the unversioned store"}")
    }

    private companion object {
        val logger: Logger = Logger.getLogger(DefaultPluginStorageService::class.java.name)
    }
}
