package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleRawAgentPlugin

class JetWhaleAgentPluginService(
    private val plugins: List<JetWhaleRawAgentPlugin>,
) {
    fun activatePlugins(vararg ids: String, baseSender: (String, String) -> Unit) {
        plugins
            .filter { it.pluginId in ids }
            .forEach { plugin ->
                plugin.activate { payload ->
                    baseSender(plugin.pluginId, payload)
                }
            }
    }

    fun deactivatePlugins(vararg ids: String) {
        plugins
            .filter { it.pluginId in ids }
            .forEach { it.deactivate() }
    }

    fun deactivateAllPlugins() {
        plugins.forEach { it.deactivate() }
    }

    fun getPluginById(id: String): JetWhaleRawAgentPlugin? {
        return plugins.firstOrNull { it.pluginId == id }
    }
}
