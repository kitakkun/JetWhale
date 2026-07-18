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
    fun `release host installs the release artifact from Maven Central`() {
        assertEquals(
            MavenCoordinates(
                groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
                artifactId = "example-plugin",
                version = "1.0.0",
                repositoryUrl = MavenCoordinates.MAVEN_CENTRAL_URL,
            ),
            plugin.coordinatesFor(HostVersionInfo("1.0.0")),
        )
    }

    @Test
    fun `snapshot host installs the matching snapshot from the snapshots repository`() {
        assertEquals(
            MavenCoordinates(
                groupId = OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID,
                artifactId = "example-plugin",
                version = "1.0.0-SNAPSHOT",
                repositoryUrl = OfficialPlugin.MAVEN_SNAPSHOTS_URL,
            ),
            plugin.coordinatesFor(HostVersionInfo("1.0.0-SNAPSHOT")),
        )
    }

    @Test
    fun `host version snapshot detection`() {
        assertEquals(false, HostVersionInfo("1.0.0-alpha08").isSnapshot)
        assertEquals(true, HostVersionInfo("1.0.0-alpha08-SNAPSHOT").isSnapshot)
    }
}
