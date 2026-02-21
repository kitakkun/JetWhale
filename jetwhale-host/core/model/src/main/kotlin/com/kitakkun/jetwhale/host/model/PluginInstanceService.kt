package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin

interface PluginInstanceService {
    fun unloadPluginInstanceForSession(sessionId: String)
    fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleRawHostPlugin?
    fun unloadPluginInstancesForPlugin(pluginId: String)

    /**
     * Initializes plugin instances for the specified plugin and sessions if they don't already exist.
     * @return The set of session IDs for which new plugin instances were initialized.
     */
    fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String>
}
