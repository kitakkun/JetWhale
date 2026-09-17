package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level shape of `META-INF/jetwhale/plugin-manifest.json`. A single plugin JAR may declare
 * several plugins, so the manifest is a list — one [JetWhaleHostPluginManifest] entry per plugin.
 * Each entry names its own [JetWhaleHostPluginFactory] implementation via
 * [JetWhaleHostPluginManifest.factoryClass], which is how a single JAR can ship several plugins.
 */
@Serializable
public data class JetWhaleHostPluginManifestFile(
    public val plugins: List<JetWhaleHostPluginManifest>,
)

@Serializable
public data class JetWhaleHostPluginManifest(
    public val pluginId: String,
    public val pluginName: String,
    public val version: String,
    /**
     * Fully-qualified name of this plugin's [JetWhaleHostPluginFactory] implementation. The host loads
     * this class from the plugin JAR and instantiates it (via its no-arg constructor) to obtain the
     * plugin. Each entry pointing at its own factory is what lets one JAR provide multiple plugins.
     */
    public val factoryClass: String,
    /**
     * Whether this plugin needs an agent counterpart. When `true` (default) the plugin is only
     * available for a session whose agent advertised this `pluginId` during negotiation. When
     * `false` the plugin is **host-only** (no agent, no messaging): it is instantiated for every
     * active session regardless of negotiation — its factory must return a plain [JetWhaleHostPlugin]
     * (not a [JetWhaleMessagingHostPlugin]).
     */
    public val requiresAgent: Boolean = true,
    /**
     * Whether this plugin gets one instance per debug session ([JetWhaleHostPluginScope.SESSION],
     * the default) or a single instance for the whole host ([JetWhaleHostPluginScope.HOST]).
     * A host-scoped plugin must declare `"requiresAgent": false` — there is no session, so there is
     * no agent to talk to.
     */
    public val scope: JetWhaleHostPluginScope = JetWhaleHostPluginScope.SESSION,
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

/** How many instances of a plugin the host creates, and what each one is tied to. */
@Serializable
public enum class JetWhaleHostPluginScope {
    /** One instance per active debug session; its tools take a `sessionId`. */
    @SerialName("session")
    SESSION,

    /**
     * A single instance for the whole host, created as soon as the plugin is enabled and loaded —
     * before (and without) any session. Its MCP tools take no `sessionId`.
     */
    @SerialName("host")
    HOST,
}
