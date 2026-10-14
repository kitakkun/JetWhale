package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class McpToolRegistryTest {

    private val registry = McpToolRegistry(mock<PluginInstanceService>())

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
    fun `replacing a plugin with nothing drops it from the capable set`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))
        registry.register("com.example.b", "session-1", FakeTooledPlugin("b.greet"))

        registry.replace("com.example.a", "session-1", plugin = null)

        assertEquals(setOf("com.example.b"), registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"))
    }

    @Test
    fun `a replacement's tool description is advertised while another session still offers the tool`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))
        registry.register("com.example.a", "session-2", FakeTooledPlugin("a.greet"))

        registry.replace("com.example.a", "session-1", RevisedToolPlugin("a.greet", "Revised greeting"))

        assertEquals("Revised greeting", registry.allRegistrations().single().second.description)
        assertEquals("com.example.a", registry.pluginIdFor("a.greet", "session-2"))
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

        val error = Json.parseToJsonElement(checkNotNull(registry.dispatch("a.greet", emptyMap()))).jsonObject.getValue("error").jsonPrimitive.content

        assertContains(error, "'sessionId' is required")
        assertContains(error, "session-1, session-2")
    }

    @Test
    fun `a plugin tool called in a session it is not offered in answers with an error naming the sessions it runs in`() = runBlocking {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))

        val error = Json.parseToJsonElement(checkNotNull(registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-9"))))).jsonObject.getValue("error").jsonPrimitive.content

        assertContains(error, "'a.greet' is not available in session 'session-9'")
        assertContains(error, "session-1")
    }

    @Test
    fun `readers keep seeing a plugin's tools while it is being replaced`() {
        registry.register("com.example.a", "session-1", FakeTooledPlugin("a.greet"))
        val replacementRead = CountDownLatch(1)
        val replacementReleased = CountDownLatch(1)
        val replacement = GatedTooledPlugin(replacementRead, replacementReleased, "a.greet")

        val replacing = thread { registry.replace("com.example.a", "session-1", replacement) }
        assertTrue(replacementRead.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val toolsWhileReplacing = registry.allRegistrations().map { it.first }
        val ownerWhileReplacing = registry.pluginIdFor("a.greet", "session-1")
        replacementReleased.countDown()
        replacing.join()

        assertEquals(listOf("a.greet"), toolsWhileReplacing)
        assertEquals("com.example.a", ownerWhileReplacing)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class FakeTooledPlugin(private vararg val toolNames: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = toolNames.map(::fakeCommand)
}

@OptIn(ExperimentalJetWhaleApi::class)
private class RevisedToolPlugin(toolName: String, toolDescription: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = toolDescription
            override suspend fun execute(arguments: JetWhaleMcpArguments): String = "ok"
        },
    )
}

/** Holds whoever reads [mcpCommands] until [released], after telling [read] that it got there. */
@OptIn(ExperimentalJetWhaleApi::class)
private class GatedTooledPlugin(
    private val read: CountDownLatch,
    private val released: CountDownLatch,
    private vararg val toolNames: String,
) : JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand>
        get() {
            read.countDown()
            released.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return toolNames.map(::fakeCommand)
        }
}

private fun fakeCommand(toolName: String): JetWhaleMcpCommand = object : JetWhaleMcpCommand() {
    override val name = toolName
    override val description = "Fake tool for testing"
    override suspend fun execute(arguments: JetWhaleMcpArguments): String = "ok"
}

private const val GATE_TIMEOUT_SECONDS = 5L

private const val ROUNDS = 200
private const val THREADS = 8
