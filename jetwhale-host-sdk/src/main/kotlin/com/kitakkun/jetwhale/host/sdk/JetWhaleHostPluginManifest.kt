package com.kitakkun.jetwhale.host.sdk

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
     *
     * Exactly one of [factoryClass] or [web] must be set: a Kotlin-authored plugin names a factory;
     * a pure web plugin declares [web] instead and needs no plugin code at all.
     */
    public val factoryClass: String? = null,
    /**
     * Whether this plugin needs an agent counterpart. When `true` (default) the plugin is only
     * available for a session whose agent advertised this `pluginId` during negotiation. When
     * `false` the plugin is **host-only** (no agent, no messaging): it is instantiated for every
     * active session regardless of negotiation — its factory must return a plain [JetWhaleHostPlugin]
     * (not a [JetWhaleMessagingHostPlugin]).
     */
    public val requiresAgent: Boolean = true,
    public val agentVersionRange: AgentVersionRange? = null,
    public val icon: Icon? = null,
    /**
     * Declares a **pure web plugin**: its UI is bundled web assets rendered by an embedded browser,
     * with no plugin code to write or compile. Set this instead of [factoryClass]; the host provides
     * the plugin implementation and wires the `window.jetwhale` bridge to the agent automatically.
     */
    public val web: WebUi? = null,
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

    /**
     * Declaration of a pure web plugin's UI: the bundled entry document and its resource root inside
     * the plugin jar, plus an optional system-property name that, when set at runtime, overrides the
     * source with a dev-server URL (for hot-module reload while iterating).
     */
    @Serializable
    public data class WebUi(
        /** Entry document to open, relative to [resourceRoot], e.g. `"index.html"`. */
        public val entry: String,
        /** Resource directory inside the jar holding the built web app, e.g. `"web"`. */
        public val resourceRoot: String,
        /** System property whose value, if present, is loaded as a dev-server URL instead of the bundle. */
        public val devServerUrlProperty: String? = null,
    )
}
