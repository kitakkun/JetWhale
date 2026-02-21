package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi

@OptIn(InternalJetWhaleApi::class)
public interface JetWhaleHostPluginFactory {
    /**
     * Metadata about the plugin.
     */
    public val meta: JetWhalePluginMetaData

    /**
     * Icon representing the plugin which can be displayed in the UI.
     */
    public val icon: JetWhalePluginIcon get() = unspecifiedPluginIcon()

    /**
     * Creates an instance of the plugin.
     * @return An instance of [JetWhaleRawHostPlugin].
     */
    public fun createPlugin(): JetWhaleRawHostPlugin

    /**
     * Checks if this plugin is compatible with the given agent version.
     * Implementations should provide the compatibility logic.
     * @param agentVersion The version of the agent to check compatibility with.
     */
    public fun isCompatibleWithAgentPlugin(agentVersion: String): Boolean = true
}
