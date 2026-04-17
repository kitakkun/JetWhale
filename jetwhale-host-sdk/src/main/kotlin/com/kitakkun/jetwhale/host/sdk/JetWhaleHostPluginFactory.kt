package com.kitakkun.jetwhale.host.sdk

public interface JetWhaleHostPluginFactory {
    /**
     * Creates an instance of the plugin.
     * @return An instance of [JetWhaleRawHostPlugin].
     */
    public fun createPlugin(): JetWhaleRawHostPlugin

    /**
     * Checks if this plugin is compatible with the given agent version.
     * @param agentVersion The version of the agent to check compatibility with.
     */
    public fun isCompatibleWithAgentPlugin(agentVersion: String): Boolean = true
}
