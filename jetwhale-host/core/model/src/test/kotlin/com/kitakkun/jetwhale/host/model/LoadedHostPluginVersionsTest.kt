package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The rule negotiation reports and the instance service binds with. */
class LoadedHostPluginVersionsTest {
    private val forOldAgents = version("1.2.0", minAgent = "1.0.0", maxAgent = "1.2.9")
    private val forNewAgents = version("1.3.0", minAgent = "1.3.0", maxAgent = null)
    private val alsoForNewAgents = version("1.10.0", minAgent = "1.3.0", maxAgent = null)
    private val versions = listOf(forOldAgents, forNewAgents, alsoForNewAgents)

    @Test
    fun `an agent gets the newest version whose range includes it`() {
        assertEquals(alsoForNewAgents, versions.newestFor("1.4.0"))
        assertEquals(forOldAgents, versions.newestFor("1.1.0"))
    }

    @Test
    fun `an agent no version accepts gets none`() {
        assertNull(versions.newestFor("0.9.0"))
    }

    @Test
    fun `the host session gets the newest version`() {
        assertEquals(alsoForNewAgents, versions.newestFor(null))
    }

    @Test
    fun `a version without a range accepts every agent`() {
        val unbounded = version("1.0.0", minAgent = null, maxAgent = null)

        assertEquals(unbounded, listOf(unbounded).newestFor("99.0.0"))
    }

    @Test
    fun `versions compare numerically component by component`() {
        assertEquals(1, PluginVersionOrder.compare("1.10.0", "1.9.2"))
        assertEquals(0, PluginVersionOrder.compare("1.2", "1.2.0"))
        assertEquals(-1, PluginVersionOrder.compare("1.2.0", "1.3.0-alpha01"))
    }

    private fun version(version: String, minAgent: String?, maxAgent: String?) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = "com.example.plugin",
            pluginName = "Example",
            version = version,
            factoryClass = "com.example.Factory",
            agentVersionRange = JetWhaleHostPluginManifest.AgentVersionRange(min = minAgent, max = maxAgent),
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = error("not created in this test")
        },
        jarPath = "/plugins/example-$version.jar",
    )
}
