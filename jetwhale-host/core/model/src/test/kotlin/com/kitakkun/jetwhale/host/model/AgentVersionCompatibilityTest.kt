package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentVersionCompatibilityTest {
    private val versionForOldAgents = loadedPluginVersion("1.2.0", minAgentVersion = "1.0.0", maxAgentVersion = "1.2.9")
    private val versionForNewAgents = loadedPluginVersion("1.3.0", minAgentVersion = "1.3.0", maxAgentVersion = null)
    private val newerVersionForNewAgents = loadedPluginVersion("1.10.0", minAgentVersion = "1.3.0", maxAgentVersion = null)
    private val versions = listOf(versionForOldAgents, versionForNewAgents, newerVersionForNewAgents)

    @Test
    fun `an agent gets the newest version whose range includes it`() {
        assertEquals(newerVersionForNewAgents, AgentVersionCompatibility("1.4.0").newestCompatibleOf(versions))
        assertEquals(versionForOldAgents, AgentVersionCompatibility("1.1.0").newestCompatibleOf(versions))
    }

    @Test
    fun `an agent no version accepts gets none`() {
        assertNull(AgentVersionCompatibility("0.9.0").newestCompatibleOf(versions))
    }

    @Test
    fun `the host session gets the newest version`() {
        assertEquals(newerVersionForNewAgents, AgentVersionCompatibility(null).newestCompatibleOf(versions))
    }

    @Test
    fun `a version without a range accepts every agent`() {
        val unboundedVersion = loadedPluginVersion("1.0.0", minAgentVersion = null, maxAgentVersion = null)

        assertEquals(unboundedVersion, AgentVersionCompatibility("99.0.0").newestCompatibleOf(listOf(unboundedVersion)))
    }

    @Test
    fun `the newest of versions that rank the same does not depend on their order`() {
        val shortVersion = loadedPluginVersion("1.2", minAgentVersion = null, maxAgentVersion = null)
        val longVersion = loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null)

        assertEquals(AgentVersionCompatibility(null).newestCompatibleOf(listOf(shortVersion, longVersion)), AgentVersionCompatibility(null).newestCompatibleOf(listOf(longVersion, shortVersion)))
    }

    @Test
    fun `a range bound compares release numbers only`() {
        val releaseBoundedVersion = loadedPluginVersion("2.0.0", minAgentVersion = "1.3.0", maxAgentVersion = "1.4")

        assertEquals(releaseBoundedVersion, AgentVersionCompatibility("1.3.0-alpha01").newestCompatibleOf(listOf(releaseBoundedVersion)))
        assertEquals(releaseBoundedVersion, AgentVersionCompatibility("1.4.0").newestCompatibleOf(listOf(releaseBoundedVersion)))
    }

    private fun loadedPluginVersion(version: String, minAgentVersion: String?, maxAgentVersion: String?) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = "com.example.plugin",
            pluginName = "Example",
            version = version,
            factoryClass = "com.example.Factory",
            agentVersionRange = JetWhaleHostPluginManifest.AgentVersionRange(min = minAgentVersion, max = maxAgentVersion),
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = error("not created in this test")
        },
        jarPath = "/plugins/example-$version.jar",
    )
}
