package com.kitakkun.jetwhale.host.model

/**
 * An officially published JetWhale plugin that can be installed without entering Maven
 * coordinates by hand. Official plugin artifacts are released in lockstep with the host under the
 * host's own version, so the concrete coordinates are derived from [HostVersionInfo] at install
 * time (snapshot hosts install the matching `-SNAPSHOT` artifact from the snapshots repository).
 */
data class OfficialPlugin(
    /** The `pluginId` the plugin declares in its manifest; used to mark it as already installed. */
    val pluginId: String,
    val displayName: String,
    val description: String,
    val artifactId: String,
) {
    /**
     * Install candidates in the order to attempt them. A snapshot host installs the matching
     * `-SNAPSHOT` from the snapshots repository. A release host prefers the release artifact from
     * Maven Central, but falls back to the matching snapshot build for host versions whose plugin
     * release has not been published yet (e.g. a locally built host of an unreleased version).
     */
    fun installCandidatesFor(hostVersion: HostVersionInfo): List<MavenCoordinates> = if (hostVersion.isSnapshot) {
        listOf(snapshotCoordinates(hostVersion.version))
    } else {
        listOf(
            MavenCoordinates(
                groupId = OFFICIAL_PLUGIN_GROUP_ID,
                artifactId = artifactId,
                version = hostVersion.version,
                repositoryUrl = MavenCoordinates.MAVEN_CENTRAL_URL,
            ),
            snapshotCoordinates("${hostVersion.version}-SNAPSHOT"),
        )
    }

    private fun snapshotCoordinates(version: String): MavenCoordinates = MavenCoordinates(
        groupId = OFFICIAL_PLUGIN_GROUP_ID,
        artifactId = artifactId,
        version = version,
        repositoryUrl = MavenCoordinates.MAVEN_SNAPSHOTS_URL,
    )

    companion object {
        const val OFFICIAL_PLUGIN_GROUP_ID = "com.kitakkun.jetwhale"
    }
}

object OfficialPluginCatalog {
    val plugins: List<OfficialPlugin> = listOf(
        OfficialPlugin(
            pluginId = "com.kitakkun.jetwhale.network",
            displayName = "Network Inspector",
            description = "Inspect and mock the HTTP traffic of connected debug sessions.",
            artifactId = "jetwhale-network-inspector",
        ),
    )
}
