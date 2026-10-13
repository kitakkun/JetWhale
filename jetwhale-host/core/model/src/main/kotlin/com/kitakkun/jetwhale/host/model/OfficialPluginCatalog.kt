package com.kitakkun.jetwhale.host.model

/**
 * An officially published JetWhale plugin that can be installed without entering Maven
 * coordinates by hand. It carries no version: [OfficialPluginRelease] derives the concrete
 * coordinates from the host's own version.
 *
 * @property pluginId The `pluginId` the plugin declares in its manifest; used to mark it as already
 * installed.
 * @property agentArtifactId The app-side library an app adds for this plugin, released under the
 * same version as the host plugin; null for a host-only plugin, which needs nothing in the app.
 * @property agentRegistration What the app passes to `register(...)` in `startJetWhale`, when one
 * expression does it; null when the plugin's guide has to explain the choice.
 * @property guidePath The plugin's page under the documentation site's `guide/`.
 */
data class OfficialPlugin(
    val pluginId: String,
    val displayName: String,
    val description: String,
    val artifactId: String,
    val agentArtifactId: String?,
    val agentRegistration: String?,
    val guidePath: String,
) {
    val guideUrl: String get() = "$DOCUMENTATION_URL/guide/$guidePath"

    companion object {
        const val OFFICIAL_PLUGIN_GROUP_ID = "com.kitakkun.jetwhale"
        const val DOCUMENTATION_URL = "https://kitakkun.github.io/JetWhale"
    }
}

object OfficialPluginCatalog {
    val plugins: List<OfficialPlugin> = listOf(
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.network",
            displayName = "Network Inspector",
            description = "Inspect and mock the HTTP traffic of connected debug sessions.",
            artifactId = "jetwhale-network-inspector",
            agentArtifactId = "jetwhale-network-inspector-agent",
            agentRegistration = null,
            guidePath = "network-inspector",
        ),
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.nav3",
            displayName = "Nav3 Navigator",
            description = "Inspect and drive the Navigation 3 back stack of connected debug sessions.",
            artifactId = "jetwhale-nav3-navigator",
            agentArtifactId = "jetwhale-nav3-agent",
            agentRegistration = null,
            guidePath = "nav3-navigator",
        ),
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.semantics",
            displayName = "Compose Semantics Inspector",
            description = "Browse and drive the Compose node tree of connected debug sessions.",
            artifactId = "jetwhale-compose-semantics-inspector",
            agentArtifactId = "jetwhale-compose-semantics-inspector-agent",
            agentRegistration = "JetWhaleSemanticsAgentPlugin()",
            guidePath = "compose-semantics-inspector",
        ),
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.storage",
            displayName = "Storage Inspector",
            description = "Browse the files and key-value stores of connected debug sessions.",
            artifactId = "jetwhale-storage-inspector",
            agentArtifactId = "jetwhale-storage-inspector-agent",
            agentRegistration = "JetWhaleStorageAgentPlugin.platformDefaults()",
            guidePath = "storage-inspector",
        ),
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.actions",
            displayName = "Debug Actions",
            description = "Run the debug actions connected apps register, from the host or an AI agent.",
            artifactId = "jetwhale-debug-actions",
            agentArtifactId = "jetwhale-debug-actions-agent",
            agentRegistration = null,
            guidePath = "debug-actions",
        ),
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.mirror",
            displayName = "Device Mirror",
            description = "Mirror Android devices, iOS simulators and iPhones, drive Android devices and simulators, and record their screens.",
            artifactId = "jetwhale-device-mirror",
            agentArtifactId = null,
            agentRegistration = null,
            guidePath = "device-mirror",
        ),
    )
}
