package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

interface EnabledPluginsRepository {
    val enabledPluginIdsFlow: Flow<Set<String>>
    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean)
    suspend fun isPluginEnabled(pluginId: String): Boolean
}
