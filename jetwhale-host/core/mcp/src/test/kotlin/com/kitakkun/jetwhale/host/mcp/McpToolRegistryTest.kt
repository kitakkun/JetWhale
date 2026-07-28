package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `pluginIdFor resolves the owner of a tool for a session`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        assertEquals("com.example.a", registry.pluginIdFor("a.greet", "session-1"))
        assertEquals(null, registry.pluginIdFor("a.greet", "session-2"))
        assertEquals(null, registry.pluginIdFor("nope", "session-1"))
    }

    @Test
    fun `dispatch hands back the command's own result`() = runBlocking {
        val plugin = FakeTooledPlugin("a.greet")
        registry.register("com.example.a", "session-1", plugin)
        every { pluginInstanceService.getPluginInstanceForSession("com.example.a", "session-1") } returns plugin

        val result = registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-1")))

        assertEquals(JetWhaleMcpResult.text("ok"), result)
    }

    @Test
    fun `dispatch turns a caller mistake into a failed result rather than throwing`() = runBlocking {
        val plugin = RejectingPlugin("a.reject")
        registry.register("com.example.a", "session-1", plugin)
        every { pluginInstanceService.getPluginInstanceForSession("com.example.a", "session-1") } returns plugin

        val result = registry.dispatch("a.reject", mapOf("sessionId" to JsonPrimitive("session-1")))

        assertEquals(true, result?.isError)
        assertEquals(listOf(JetWhaleMcpContent.Text("no widget with id: 7")), result?.content)
    }

    @Test
    fun `dispatch reports an unroutable call as no result at all`() = runBlocking {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        // A tool nobody registered, and a session that does not have the tool: neither is a plugin
        // failure, so neither may be answered with an error result the plugin never produced.
        assertNull(registry.dispatch("a.missing", mapOf("sessionId" to JsonPrimitive("session-1"))))
        assertNull(registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-2"))))
        assertNull(registry.dispatch("a.greet", emptyMap()))
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class FakeTooledPlugin(private vararg val toolNames: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = toolNames.map { toolName ->
        object : JetWhaleMcpTextCommand() {
            override val name = toolName
            override val description = "Fake tool for testing"
            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = "ok"
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class RejectingPlugin(private val toolName: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Always rejects the call"
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = throw JetWhaleMcpArgumentException("no widget with id: 7")
        },
    )
}
