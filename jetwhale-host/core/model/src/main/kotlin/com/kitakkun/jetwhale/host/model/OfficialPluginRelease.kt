package com.kitakkun.jetwhale.host.model

/**
 * How official plugins are released for a host of [hostVersionInfo]. They ship in lockstep with the
 * host, under the host's own version, and so does the agent runtime their app-side libraries run on.
 */
class OfficialPluginRelease(private val hostVersionInfo: HostVersionInfo) {
    /** The Gradle coordinates of the agent runtime an app adds next to a plugin's agent library. */
    val agentRuntimeCoordinates: String = "${OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID}:jetwhale-agent-runtime:${hostVersionInfo.version}"

    /** The Gradle coordinates of [plugin]'s agent library, or null for a host-only plugin. */
    fun agentCoordinatesOf(plugin: OfficialPlugin): String? = plugin.agentArtifactId?.let { "${OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID}:$it:${hostVersionInfo.version}" }

    /**
     * The coordinates to install [plugin] from, in the order to attempt them. A snapshot host
     * installs the matching `-SNAPSHOT` from the snapshots repository. A release host prefers the
     * release artifact from Maven Central, but falls back to the matching snapshot build for host
     * versions whose plugin release has not been published yet (e.g. a locally built host of an
     * unreleased version).
     */
    fun installCandidatesOf(plugin: OfficialPlugin): List<MavenCoordinates> = if (hostVersionInfo.isSnapshot) {
        listOf(snapshotCoordinatesOf(plugin, hostVersionInfo.version))
    } else {
        listOf(
            MavenCoordinates(
                groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
                artifactId = plugin.artifactId,
                version = hostVersionInfo.version,
                repositoryUrl = MavenCoordinates.MAVEN_CENTRAL_URL,
            ),
            snapshotCoordinatesOf(plugin, "${hostVersionInfo.version}-SNAPSHOT"),
        )
    }

    private fun snapshotCoordinatesOf(plugin: OfficialPlugin, version: String): MavenCoordinates = MavenCoordinates(
        groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
        artifactId = plugin.artifactId,
        version = version,
        repositoryUrl = MavenCoordinates.MAVEN_SNAPSHOTS_URL,
    )
}
