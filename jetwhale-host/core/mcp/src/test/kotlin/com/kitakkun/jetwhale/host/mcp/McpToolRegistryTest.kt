package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class McpToolRegistryTest {

    private val pluginInstanceService = mock<PluginInstanceService>()
    private val registry = McpToolRegistry(pluginInstanceService)

    @Test
    fun `no plugins are reported as MCP-capable before anything registers`() {
        assertEquals(emptyMap(), registry.mcpCapablePluginsFlow.value.toolsBySessionAndPlugin)
    }

    @Test
    fun `a registered plugin is reported as MCP-capable for its own session only`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        val capable = registry.mcpCapablePluginsFlow.value
        assertEquals(setOf("com.example.a"), capable.pluginIdsFor("session-1"))
        assertEquals(emptySet(), capable.pluginIdsFor("session-2"))
        assertEquals(emptySet(), capable.pluginIdsFor(null))
    }

    @Test
    fun `plugins registered in the same session accumulate`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))
        registry.register("com.example.b", "session-1", FakeTooledPlugin("b.greet"))

        assertEquals(
            setOf("com.example.a", "com.example.b"),
            registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"),
        )
    }

    @Test
    fun `unregistering a plugin drops it from the capable set`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))
        registry.register("com.example.b", "session-1", FakeTooledPlugin("b.greet"))

        registry.unregister("com.example.a", "session-1")

        assertEquals(setOf("com.example.b"), registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"))
    }

    @Test
    fun `a plugin declaring no MCP commands is not reported as capable`() {
        registry.register("com.example.empty", "session-1", FakeTooledPlugin())

        assertEquals(emptySet(), registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"))
    }

    @Test
    fun `clear empties the capable set`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        registry.clear()

        assertEquals(emptyMap(), registry.mcpCapablePluginsFlow.value.toolsBySessionAndPlugin)
    }

    // -- host-scoped plugins ----------------------------------------------------------------

    @Test
    fun `a host-scoped plugin's tools are registered without a session`() {
        registry.register("com.example.host", sessionId = null, plugin = FakeTooledPlugin("host.greet"))

        val registration = registry.allHostScopedRegistrations().single()
        assertEquals("host.greet", registration.name)
        assertEquals("com.example.host", registration.pluginId)
        // A host-scoped tool declares exactly its own parameters; no sessionId is injected anywhere.
        assertTrue(registration.descriptor.parameters.isEmpty())
        assertTrue(registry.allRegistrations().isEmpty())
    }

    @Test
    fun `a host-scoped plugin is reported as MCP-capable whatever the session`() {
        registry.register("com.example.host", sessionId = null, plugin = FakeTooledPlugin("host.greet"))

        val capable = registry.mcpCapablePluginsFlow.value
        assertEquals(setOf("com.example.host"), capable.pluginIdsFor(null))
        assertEquals(listOf("host.greet"), capable.toolsFor(null, "com.example.host").map { it.name })
    }

    @Test
    fun `a host-scoped tool is dispatched without a sessionId argument`() = runBlocking {
        val plugin = FakeTooledPlugin("host.greet")
        every { pluginInstanceService.getHostScopedInstance("com.example.host") } returns plugin
        registry.register("com.example.host", sessionId = null, plugin = plugin)

        val result = registry.dispatch("host.greet", emptyMap())

        assertEquals("ok", requireNotNull(result).text)
    }

    @Test
    fun `unregistering a host-scoped plugin drops its tools`() {
        registry.register("com.example.host", sessionId = null, plugin = FakeTooledPlugin("host.greet"))

        registry.unregister("com.example.host", sessionId = null)

        assertTrue(registry.allHostScopedRegistrations().isEmpty())
        assertEquals(emptySet(), registry.mcpCapablePluginsFlow.value.pluginIdsFor(null))
    }

    @Test
    fun `a session-scoped tool call without a sessionId is not dispatched`() = runBlocking {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        assertNull(registry.dispatch("a.greet", emptyMap()))
        assertNull(registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-2"))))
    }

    @Test
    fun `pluginIdFor resolves the owner of a tool for a session`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        assertEquals("com.example.a", registry.pluginIdFor("a.greet", "session-1"))
        assertEquals(null, registry.pluginIdFor("a.greet", "session-2"))
        assertEquals(null, registry.pluginIdFor("nope", "session-1"))
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class FakeTooledPlugin(private vararg val toolNames: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = toolNames.map { toolName ->
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Fake tool for testing"
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.text("ok")
        }
    }
}
