package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest

/**
 * One loaded version of a plugin.
 *
 * @property jarPath The jar this version was loaded from. Several versions of one plugin come from
 *   different jars, and so from different classloaders.
 */
data class LoadedHostPlugin(
    val manifest: JetWhaleHostPluginManifest,
    val factory: JetWhaleHostPluginFactory,
    val jarPath: String,
)

/**
 * The newest of these versions an agent at [agentVersion] can use, or the newest of all when
 * [agentVersion] is null (the host session, which has no agent). Null when none fits.
 */
fun List<LoadedHostPlugin>.newestFor(agentVersion: String?): LoadedHostPlugin? = filter { loaded ->
    agentVersion == null || loaded.manifest.agentVersionRange?.accepts(agentVersion) ?: true
}.maxWithOrNull(compareBy(PluginVersionOrder) { it.manifest.version })

/**
 * Whether an agent plugin at [agentVersion] falls inside this range; open ends are unbounded. A bound
 * compares release numbers only, so a pre-release of `1.3.0` is inside a range that starts at `1.3.0`.
 */
private fun JetWhaleHostPluginManifest.AgentVersionRange.accepts(agentVersion: String): Boolean = (min?.let { PluginVersionOrder.compareReleases(agentVersion, it) >= 0 } ?: true) &&
    (max?.let { PluginVersionOrder.compareReleases(agentVersion, it) <= 0 } ?: true)
