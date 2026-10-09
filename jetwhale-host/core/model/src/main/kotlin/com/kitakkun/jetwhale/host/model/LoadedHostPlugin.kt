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
