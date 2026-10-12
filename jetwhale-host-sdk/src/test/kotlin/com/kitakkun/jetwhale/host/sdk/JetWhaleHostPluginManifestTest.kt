package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JetWhaleHostPluginManifestTest {
    @Test
    fun `manifests that differ only in runsInHostSession are not equal`() {
        assertNotEquals(manifest(runsInHostSession = false), manifest(runsInHostSession = true))
        assertEquals(manifest(runsInHostSession = true), manifest(runsInHostSession = true))
    }

    @Test
    fun `runsInHostSession takes part in the hash code`() {
        assertNotEquals(manifest(runsInHostSession = false).hashCode(), manifest(runsInHostSession = true).hashCode())
    }

    @Test
    fun `toString shows runsInHostSession`() {
        assertTrue("runsInHostSession=true" in manifest(runsInHostSession = true).toString())
    }

    @Test
    fun `a manifest entry without runsInHostSession runs in app sessions only`() {
        val decoded = Json.decodeFromString<JetWhaleHostPluginManifest>(
            """{"pluginId":"com.example.plugin","pluginName":"Example","version":"1.0.0","factoryClass":"com.example.ExampleFactory"}""",
        )

        assertFalse(decoded.runsInHostSession)
    }

    private fun manifest(runsInHostSession: Boolean) = JetWhaleHostPluginManifest(
        pluginId = "com.example.plugin",
        pluginName = "Example",
        version = "1.0.0",
        factoryClass = "com.example.ExampleFactory",
        runsInHostSession = runsInHostSession,
    )
}
