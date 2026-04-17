package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest

data class LoadedHostPlugin(
    val manifest: JetWhaleHostPluginManifest,
    val factory: JetWhaleHostPluginFactory,
)
