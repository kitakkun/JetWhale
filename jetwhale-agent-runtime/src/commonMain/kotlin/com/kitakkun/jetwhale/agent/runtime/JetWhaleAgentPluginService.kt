package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleRawAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi

@OptIn(InternalJetWhaleApi::class)
internal class JetWhaleAgentPluginService(
    private val plugins: List<JetWhaleRawAgentPlugin>,
) {
    fun activatePlugins(vararg ids: String, sender: PluginAwareMessageSender) {
        plugins
            .filter { it.pluginId in ids }
            .forEach { plugin ->
                plugin.activate { messages ->
                    sender.send(plugin.pluginId, *messages)
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

    fun getPluginById(id: String): JetWhaleRawAgentPlugin? = plugins.firstOrNull { it.pluginId == id }
}
