package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.Serializable

/**
 * Top-level shape of `META-INF/jetwhale/plugin-manifest.json`. A single plugin JAR may declare
 * several plugins, so the manifest is a list — one [JetWhaleHostPluginManifest] entry per plugin.
 * Each entry names its own [JetWhaleHostPluginFactory] implementation via
 * [JetWhaleHostPluginManifest.factoryClass], which is how a single JAR can ship several plugins.
 */
@Serializable
public class JetWhaleHostPluginManifestFile(
    public val plugins: List<JetWhaleHostPluginManifest>,
) {
    override fun equals(other: Any?): Boolean = other is JetWhaleHostPluginManifestFile && plugins == other.plugins

    override fun hashCode(): Int = plugins.hashCode()

    override fun toString(): String = "JetWhaleHostPluginManifestFile(plugins=$plugins)"
}

/**
 * One plugin entry of `META-INF/jetwhale/plugin-manifest.json`.
 *
 * @property factoryClass Fully-qualified name of this plugin's [JetWhaleHostPluginFactory]
 *   implementation. The host loads this class from the plugin JAR and instantiates it (via its
 *   no-arg constructor) to obtain the plugin. Each entry pointing at its own factory is what lets
 *   one JAR provide multiple plugins.
 * @property requiresAgent Whether this plugin needs an agent counterpart. When `true` (default) the
 *   plugin gets an instance in each app session whose agent advertised this `pluginId` during
 *   negotiation. When `false` the plugin is **host-only** (no agent, no messaging): it gets one
 *   instance, in the host session, which needs no app — its factory must return a plain
 *   [JetWhaleHostPlugin] (not a [JetWhaleMessagingHostPlugin]).
 * @property runsInHostSession Whether a plugin that needs an agent also gets an instance in the host
 *   session, next to its instances in app sessions. That instance has no agent to talk to: see
 *   [JetWhaleHostPlugin.isInHostSession]. Ignored when [requiresAgent] is `false`, since such a plugin
 *   runs in the host session only. A host older than this key ignores it and runs the plugin in app
 *   sessions only.
 */
@Serializable
public class JetWhaleHostPluginManifest(
    public val pluginId: String,
    public val pluginName: String,
    public val version: String,
    public val factoryClass: String,
    public val requiresAgent: Boolean = true,
    public val runsInHostSession: Boolean = false,
    public val agentVersionRange: AgentVersionRange? = null,
    public val icon: Icon? = null,
) {
    override fun equals(other: Any?): Boolean = other is JetWhaleHostPluginManifest &&
        pluginId == other.pluginId &&
        pluginName == other.pluginName &&
        version == other.version &&
        factoryClass == other.factoryClass &&
        requiresAgent == other.requiresAgent &&
        runsInHostSession == other.runsInHostSession &&
        agentVersionRange == other.agentVersionRange &&
        icon == other.icon

    override fun hashCode(): Int {
        var result = pluginId.hashCode()
        result = 31 * result + pluginName.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + factoryClass.hashCode()
        result = 31 * result + requiresAgent.hashCode()
        result = 31 * result + runsInHostSession.hashCode()
        result = 31 * result + agentVersionRange.hashCode()
        result = 31 * result + icon.hashCode()
        return result
    }

    override fun toString(): String = "JetWhaleHostPluginManifest(pluginId=$pluginId, " +
        "pluginName=$pluginName, version=$version, factoryClass=$factoryClass, " +
        "requiresAgent=$requiresAgent, runsInHostSession=$runsInHostSession, agentVersionRange=$agentVersionRange, icon=$icon)"

    /**
     * Specifies the range of agent plugin versions this host plugin is compatible with.
     * A null [min] means no lower bound; a null [max] means no upper bound.
     * If [agentVersionRange] itself is null, the plugin is assumed compatible with all agent versions.
     */
    @Serializable
    public class AgentVersionRange(
        public val min: String? = null,
        public val max: String? = null,
    ) {
        override fun equals(other: Any?): Boolean = other is AgentVersionRange && min == other.min && max == other.max

        override fun hashCode(): Int = 31 * min.hashCode() + max.hashCode()

        override fun toString(): String = "AgentVersionRange(min=$min, max=$max)"
    }

    @Serializable
    public class Icon(
        public val activePath: String? = null,
        public val inactivePath: String? = null,
    ) {
        override fun equals(other: Any?): Boolean = other is Icon && activePath == other.activePath && inactivePath == other.inactivePath

        override fun hashCode(): Int = 31 * activePath.hashCode() + inactivePath.hashCode()

        override fun toString(): String = "Icon(activePath=$activePath, inactivePath=$inactivePath)"
    }
}
