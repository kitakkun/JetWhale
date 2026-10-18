package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.api.provider.Property

/**
 * Configuration for the `com.kitakkun.jetwhale.protocol` plugin, applied to a JetWhale plugin's
 * protocol module: the module of `@Serializable` messages that its agent and host plugin share.
 *
 * The plugin generates a `JetWhalePluginContract` named [contractQualifiedName] from these values and
 * the module's `version`, which is the plugin's release version.
 */
interface JetWhaleProtocolExtension {
    /** The plugin's id, as in its host plugin manifest. */
    val pluginId: Property<String>

    /**
     * The plugin's protocol revision. It starts at 1 and goes up by one with each breaking change of
     * the wire. `checkWireSchema` fails while the committed dump records another revision, and
     * `dumpWireSchema` refuses a breaking change that comes without a new one.
     */
    val protocolRevision: Property<Int>

    /**
     * The fully qualified name of the generated top-level `JetWhalePluginContract` property, such as
     * `com.example.myplugin.protocol.MyPluginContract`.
     */
    val contractQualifiedName: Property<String>
}
