package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.BoundPluginVersions
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalJetWhaleApi::class)
class McpToolRegistryTest {

    private val boundVersions = MutableStateFlow(BoundPluginVersions.Empty)
    private val instances = mutableMapOf<Pair<String, String>, JetWhaleHostPlugin>()
    private val pluginInstanceService = mock<PluginInstanceService> {
        every { boundVersionsFlow } returns boundVersions
        every { getPluginInstanceForSession(any(), any()) } calls { (pluginId: String, sessionId: String) -> instances[pluginId to sessionId] }
    }
    private val registry = McpToolRegistry(pluginInstanceService)

    @Test
    fun `no plugins are reported as MCP-capable before anything registers`() {
        assertEquals(emptyMap(), registry.mcpCapablePluginsFlow.value.toolsBySessionAndPlugin)
    }

    @Test
    fun `a registered plugin is reported as MCP-capable for its own session only`() {
        registry.register("com.example.a", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("a.greet"))

        val capable = registry.mcpCapablePluginsFlow.value
        assertEquals(setOf("com.example.a"), capable.pluginIdsFor("session-1"))
        assertEquals(emptySet(), capable.pluginIdsFor("session-2"))
        assertEquals(emptySet(), capable.pluginIdsFor(null))
    }

    @Test
    fun `plugins registered in the same session accumulate`() {
        registry.register("com.example.a", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("a.greet"))
        registry.register("com.example.b", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("b.greet"))

        assertEquals(
            setOf("com.example.a", "com.example.b"),
            registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"),
        )
    }

    @Test
    fun `plugins registering at the same time from several threads are all reported`() {
        // The lost update depends on timing, so one attempt may pass by luck; many rounds do not.
        repeat(ROUNDS) { round ->
            val registry = McpToolRegistry(pluginInstanceService)
            val start = CountDownLatch(1)
            val threads = (0 until THREADS).map { index ->
                thread {
                    start.await()
                    registry.register("com.example.p$index", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("p$index.greet"))
                }
            }
            start.countDown()
            threads.forEach(Thread::join)

            assertEquals(THREADS, registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1").size, "round $round")
        }
    }

    @Test
    fun `unregistering a plugin drops it from the capable set`() {
        registry.register("com.example.a", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("a.greet"))
        registry.register("com.example.b", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("b.greet"))

        registry.unregister("com.example.a", "session-1")

        assertEquals(setOf("com.example.b"), registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"))
    }

    @Test
    fun `a plugin declaring no MCP commands is not reported as capable`() {
        registry.register("com.example.empty", "session-1", version = "1.0.0", plugin = FakeTooledPlugin())

        assertEquals(emptySet(), registry.mcpCapablePluginsFlow.value.pluginIdsFor("session-1"))
    }

    @Test
    fun `clear empties the capable set`() {
        registry.register("com.example.a", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("a.greet"))

        registry.clear()

        assertEquals(emptyMap(), registry.mcpCapablePluginsFlow.value.toolsBySessionAndPlugin)
    }

    @Test
    fun `pluginIdFor resolves the owner of a tool for a session`() {
        registry.register("com.example.a", "session-1", version = "1.0.0", plugin = FakeTooledPlugin("a.greet"))

        assertEquals("com.example.a", registry.pluginIdFor("a.greet", "session-1"))
        assertEquals(null, registry.pluginIdFor("a.greet", "session-2"))
        assertEquals(null, registry.pluginIdFor("nope", "session-1"))
    }

    @Test
    fun `a tool is listed with the definition of the newest version that registers it`() {
        register(sessionId = "session-old", version = "1.2.0", FakeTooledPlugin("a.greet", description = "old"))
        register(sessionId = "session-new", version = "1.10.0", FakeTooledPlugin("a.greet", description = "new"))

        assertEquals("new", registry.allRegistrations().single().second.description)
    }

    @Test
    fun `a call runs the version bound to the target session`() = runBlocking {
        register(sessionId = "session-old", version = "1.2.0", FakeTooledPlugin("a.greet", result = "from 1.2.0"))
        register(sessionId = "session-new", version = "1.3.0", FakeTooledPlugin("a.greet", result = "from 1.3.0"))

        assertEquals("from 1.2.0", registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-old"))))
        assertEquals("from 1.3.0", registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-new"))))
    }

    @Test
    fun `a call to a tool the session's version lacks fails naming that version`() = runBlocking {
        register(sessionId = "session-new", version = "1.3.0", FakeTooledPlugin("a.greet", "a.added"))
        register(sessionId = "session-old", version = "1.2.0", FakeTooledPlugin("a.greet"))

        val result = registry.dispatch("a.added", mapOf("sessionId" to JsonPrimitive("session-old")))

        val error = Json.parseToJsonElement(result.orEmpty()).jsonObject["error"]?.jsonPrimitive?.content.orEmpty()
        assertContains(error, "1.2.0")
        assertContains(error, "session-old")
        assertEquals("com.example.a", registry.pluginIdFor("a.added", "session-old"))
    }

    @Test
    fun `an argument error from an older version names the version that ran`() = runBlocking {
        register(sessionId = "session-new", version = "1.3.0", FakeTooledPlugin("a.greet"))
        register(sessionId = "session-old", version = "1.2.0", FakeTooledPlugin("a.greet", rejectArguments = true))

        val result = registry.dispatch("a.greet", mapOf("sessionId" to JsonPrimitive("session-old")))

        val error = Json.parseToJsonElement(result.orEmpty()).jsonObject["error"]?.jsonPrimitive?.content.orEmpty()
        assertContains(error, "1.2.0")
        assertContains(error, "1.3.0")
    }

    private fun register(sessionId: String, version: String, plugin: FakeTooledPlugin) {
        instances["com.example.a" to sessionId] = plugin
        boundVersions.value = BoundPluginVersions(boundVersions.value.versionsBySession + (sessionId to mapOf("com.example.a" to version)))
        registry.register("com.example.a", sessionId, version = version, plugin = plugin)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class FakeTooledPlugin(
    private vararg val toolNames: String,
    private val description: String = "Fake tool for testing",
    private val result: String = "ok",
    private val rejectArguments: Boolean = false,
) : JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = toolNames.map { toolName ->
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = this@FakeTooledPlugin.description
            override suspend fun execute(arguments: JetWhaleMcpArguments): String {
                if (rejectArguments) throw JetWhaleMcpArgumentException("missing 'name'")
                return result
            }
        }
    }
}

private const val ROUNDS = 200
private const val THREADS = 8
