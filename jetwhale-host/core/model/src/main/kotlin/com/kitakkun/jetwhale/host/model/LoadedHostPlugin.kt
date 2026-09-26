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
