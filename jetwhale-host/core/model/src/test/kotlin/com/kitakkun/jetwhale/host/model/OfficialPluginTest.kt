package com.kitakkun.jetwhale.host.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialPluginTest {
    private val plugin = OfficialPlugin(
        pluginId = "com.example.plugin",
        displayName = "Example",
        description = "Example plugin",
        artifactId = "example-plugin",
    )

    @Test
    fun `release host prefers the release artifact and falls back to its snapshot`() {
        assertEquals(
            listOf(
                MavenCoordinates(
                    groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
                    artifactId = "example-plugin",
                    version = "1.0.0",
                    repositoryUrl = MavenCoordinates.MAVEN_CENTRAL_URL,
                ),
                MavenCoordinates(
                    groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
                    artifactId = "example-plugin",
                    version = "1.0.0-SNAPSHOT",
                    repositoryUrl = MavenCoordinates.MAVEN_SNAPSHOTS_URL,
                ),
            ),
            plugin.installCandidatesFor(HostVersionInfo("1.0.0")),
        )
    }

    @Test
    fun `snapshot host installs only the matching snapshot from the snapshots repository`() {
        assertEquals(
            listOf(
                MavenCoordinates(
                    groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
                    artifactId = "example-plugin",
                    version = "1.0.0-SNAPSHOT",
                    repositoryUrl = MavenCoordinates.MAVEN_SNAPSHOTS_URL,
                ),
            ),
            plugin.installCandidatesFor(HostVersionInfo("1.0.0-SNAPSHOT")),
        )
    }

    @Test
    fun `host version snapshot detection`() {
        assertEquals(false, HostVersionInfo("1.0.0-alpha08").isSnapshot)
        assertEquals(true, HostVersionInfo("1.0.0-alpha08-SNAPSHOT").isSnapshot)
    }
}
