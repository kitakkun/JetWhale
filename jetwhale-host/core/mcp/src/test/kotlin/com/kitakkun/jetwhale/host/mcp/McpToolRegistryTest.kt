package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `plugins registering at the same time from several threads are all reported`() {
        repeat(ROUNDS) { round ->
            val registry = McpToolRegistry(mock<PluginInstanceService>())
            val start = CountDownLatch(1)
            val threads = (0 until THREADS).map { index ->
                thread {
                    start.await()
                    registry.register("com.example.p$index", "session-1", FakeTooledPlugin("p$index.greet"))
                }
            }
            start.countDown()
            threads.forEach(Thread::join)

            assertEquals(THREADS, registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1").size, "round $round")
        }
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
    fun `a plugin tool called without a sessionId answers with an error naming the sessions it runs in`() = runBlocking {
        registry.register("com.example.a", "session-2", FakeTooledPlugin("a.greet"))
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        val result = checkNotNull(registry.dispatch("a.greet", emptyMap()))

        assertTrue(result.isError)
        assertContains(result.text(), "'sessionId' is required")
        assertContains(result.text(), "session-1, session-2")
    }

    @Test
    fun `a plugin tool called in a session it is not offered in answers with an error naming the sessions it runs in`() = runBlocking {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        val result = checkNotNull(registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-9"))))

        assertTrue(result.isError)
        assertContains(result.text(), "'a.greet' is not available in session 'session-9'")
        assertContains(result.text(), "session-1")
    }

    @Test
    fun `dispatch hands back the command's own result`() = runBlocking {
        assertEquals(JetWhaleMcpResult.text("ok"), dispatchTo(FakeTooledPlugin("a.greet"), "a.greet"))
    }

    @Test
    fun `dispatch turns a thrown argument mistake into a failed result`() = runBlocking {
        val result = dispatchTo(ThrowingPlugin("a.reject", JetWhaleMcpArgumentException("no widget with id: 7")), "a.reject")

        assertTrue(result.isError)
        assertEquals("no widget with id: 7", result.text())
    }

    @Test
    fun `dispatch turns any thrown tool failure into a failed result`() = runBlocking {
        val result = dispatchTo(ThrowingPlugin("a.fail", JetWhaleMcpException("the device disconnected")), "a.fail")

        assertTrue(result.isError)
        assertEquals("the device disconnected", result.text())
    }

    @Test
    fun `dispatch refuses a successful answer that bypasses the declared output`() {
        val plugin = BypassingOutputPlugin("a.measure")

        val exception = assertFailsWith<IllegalStateException> { runBlocking { dispatchTo(plugin, "a.measure") } }
        assertContains(exception.message.orEmpty(), "declares an output but answered without it")
    }

    @Test
    fun `dispatch of a tool nobody offers answers nothing`() = runBlocking {
        assertNull(registry.dispatch("a.missing", mapOf("sessionId" to JsonPrimitive("session-1"))))
    }

    private suspend fun <P> dispatchTo(plugin: P, toolName: String): JetWhaleMcpResult where P : JetWhaleHostPlugin, P : JetWhaleMcpCapablePlugin {
        registry.register("com.example.a", "session-1", plugin)
        every { pluginInstanceService.getPluginInstanceForSession("com.example.a", "session-1") } returns plugin
        return checkNotNull(registry.dispatch(toolName, mapOf("sessionId" to JsonPrimitive("session-1"))))
    }

    private fun JetWhaleMcpResult.text(): String = (content.single() as JetWhaleMcpContent.Text).text
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
private class ThrowingPlugin(private val toolName: String, private val failure: Exception) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Always fails"
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = throw failure
        },
    )
}

@Serializable
private data class Measurement(val widthPx: Int)

@OptIn(ExperimentalJetWhaleApi::class)
private class BypassingOutputPlugin(private val toolName: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Declares an output, then answers around it"
            private val measurement = serializableOutput<Measurement>()
            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.json(buildJsonObject { put("widthPx", "wide") })
        },
    )
}

private const val ROUNDS = 200
private const val THREADS = 8
