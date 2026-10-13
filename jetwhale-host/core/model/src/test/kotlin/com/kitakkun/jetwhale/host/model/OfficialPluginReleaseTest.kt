package com.kitakkun.jetwhale.host.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialPluginReleaseTest {
    private val plugin = OfficialPlugin(
        pluginId = "com.example.plugin",
        displayName = "Example",
        description = "Example plugin",
        artifactId = "example-plugin",
        agentArtifactId = "example-plugin-agent",
        agentRegistration = null,
        guidePath = "example",
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
            OfficialPluginRelease(HostVersionInfo("1.0.0")).installCandidatesOf(plugin),
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
            OfficialPluginRelease(HostVersionInfo("1.0.0-SNAPSHOT")).installCandidatesOf(plugin),
        )
    }

    @Test
    fun `host version snapshot detection`() {
        assertEquals(false, HostVersionInfo("1.0.0-alpha08").isSnapshot)
        assertEquals(true, HostVersionInfo("1.0.0-alpha08-SNAPSHOT").isSnapshot)
    }

    @Test
    fun `the agent coordinates follow the host's version`() {
        assertEquals("com.kitakkun.jetwhale:example-plugin-agent:1.2.0-SNAPSHOT", OfficialPluginRelease(HostVersionInfo("1.2.0-SNAPSHOT")).agentCoordinatesOf(plugin))
    }

    @Test
    fun `the agent runtime follows the host's version`() {
        assertEquals("com.kitakkun.jetwhale:jetwhale-agent-runtime:1.2.0", OfficialPluginRelease(HostVersionInfo("1.2.0")).agentRuntimeCoordinates)
    }

    @Test
    fun `a host-only plugin has no agent coordinates`() {
        assertNull(OfficialPluginRelease(HostVersionInfo("1.2.0")).agentCoordinatesOf(plugin.copy(agentArtifactId = null)))
    }
}
