package com.kitakkun.jetwhale.protocol.negotiation

/**
 * What a plugin's agent and host plugin compile against: which plugin, which release, and which wire.
 *
 * The `com.kitakkun.jetwhale.protocol` Gradle plugin generates one from the protocol module's build
 * script, so the agent and the host plugin, which both depend on that module, read the same values.
 * The constructor is public for a plugin that has no protocol module.
 *
 * @param pluginId The plugin's id, as in its host plugin manifest.
 * @param releaseVersion The release version of the plugin's artifacts, such as `1.4.0`.
 * @param protocolRevision The plugin's protocol revision: 1 for the wire it had when it adopted
 * revisions, raised by one on each breaking change of that wire.
 * @param wireSchemaHash The SHA-256 of the protocol module's wire schema dump, without its revision
 * line, as lowercase hex; `null` when the module has no dump.
 */
public class JetWhalePluginContract(
    public val pluginId: String,
    public val releaseVersion: String,
    public val protocolRevision: Int,
    public val wireSchemaHash: String?,
) {
    init {
        require(protocolRevision >= 1) { "A protocol revision starts at 1, but $pluginId declares $protocolRevision." }
    }

    override fun equals(other: Any?): Boolean = other is JetWhalePluginContract &&
        pluginId == other.pluginId &&
        releaseVersion == other.releaseVersion &&
        protocolRevision == other.protocolRevision &&
        wireSchemaHash == other.wireSchemaHash

    override fun hashCode(): Int {
        var result = pluginId.hashCode()
        result = 31 * result + releaseVersion.hashCode()
        result = 31 * result + protocolRevision
        result = 31 * result + (wireSchemaHash?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "JetWhalePluginContract(pluginId=$pluginId, releaseVersion=$releaseVersion, " +
        "protocolRevision=$protocolRevision, wireSchemaHash=$wireSchemaHash)"
}
