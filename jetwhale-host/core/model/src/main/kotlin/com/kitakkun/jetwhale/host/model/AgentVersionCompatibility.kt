package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest.AgentVersionRange

/**
 * The rule that picks the loaded version of a plugin to serve an agent whose plugin is at
 * [agentVersion]: the newest whose `agentVersionRange` accepts it. A null [agentVersion] stands for
 * the host session, which has no agent and is served by the newest of all. Negotiation reports with
 * this rule and the instance service binds with it.
 */
class AgentVersionCompatibility(private val agentVersion: String?) {
    /** The newest of [loadedVersions] that serves the agent, or null when none does. */
    fun newestCompatibleOf(loadedVersions: List<LoadedHostPlugin>): LoadedHostPlugin? = loadedVersions
        .filter { it.manifest.agentVersionRange?.acceptsAgentVersion() ?: true }
        .maxWithOrNull(compareBy(PluginVersionOrder) { it.manifest.version })

    /**
     * Open ends are unbounded. A bound compares release numbers only, so a pre-release of `1.3.0` is
     * inside a range that starts at `1.3.0`.
     */
    private fun AgentVersionRange.acceptsAgentVersion(): Boolean {
        if (agentVersion == null) return true
        return (min?.let { PluginVersionOrder.compareReleases(agentVersion, it) >= 0 } ?: true) &&
            (max?.let { PluginVersionOrder.compareReleases(agentVersion, it) <= 0 } ?: true)
    }
}
