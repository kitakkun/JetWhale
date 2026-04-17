package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.Serializable

@Serializable
public data class JetWhaleHostPluginManifest(
    public val pluginId: String,
    public val pluginName: String,
    public val version: String,
    public val icon: Icon? = null,
) {
    @Serializable
    public data class Icon(
        public val activePath: String? = null,
        public val inactivePath: String? = null,
    )
}
