package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.Serializable

@Serializable
public data class JetWhaleHostPluginManifest(
    public val pluginId: String,
    public val pluginName: String,
    public val version: String,
    public val agentVersionRange: AgentVersionRange? = null,
    public val icon: Icon? = null,
) {
    /**
     * Specifies the range of agent plugin versions this host plugin is compatible with.
     * A null [min] means no lower bound; a null [max] means no upper bound.
     * If [agentVersionRange] itself is null, the plugin is assumed compatible with all agent versions.
     */
    @Serializable
    public data class AgentVersionRange(
        public val min: String? = null,
        public val max: String? = null,
    )

    @Serializable
    public data class Icon(
        public val activePath: String? = null,
        public val inactivePath: String? = null,
    )
}
