package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DefaultMcpServerServiceTest {

    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getLoadedPluginInstances() } returns emptyList()
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }

    private val service = DefaultMcpServerService(
        pluginInstanceService = pluginInstanceService,
        builtInTools = emptySet(),
    )

    private val host = "localhost"
    private val port = java.net.ServerSocket(0).use { it.localPort }

    /**
     * Holds [port] (0 picks a free one) on the loopback address specifically. A wildcard-bound
     * `ServerSocket(0)` would not do: BSD-derived systems let a SO_REUSEADDR socket bind
     * 127.0.0.1:p over an existing 0.0.0.0:p, so the server would start just fine and the test
     * would silently stop testing the failure path.
     */
    private fun occupyPort(port: Int = 0): java.net.ServerSocket = java.net.ServerSocket(port, 50, java.net.InetAddress.getByName(host))

    @Test
    fun `listTools returns all registered built-in tools`() = runBlocking {
        val serviceWithTools = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            builtInTools = setOf(
                FakeMcpTool("fake.toolA"),
                FakeMcpTool("fake.toolB"),
                FakeMcpTool("fake.toolC"),
            ),
        )
        val toolsPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTools.start(host, toolsPort)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$toolsPort/sse")
            try {
                assertEquals(setOf("fake.toolA", "fake.toolB", "fake.toolC"), client.listTools().tools.map { it.name }.toSet())
            } finally {
                client.close()
            }
        } finally {
            serviceWithTools.stop()
        }
    }

    @Test
    fun `built-in tool response is returned correctly via MCP`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            builtInTools = setOf(FakeMcpTool("fake.echo", response = "pong")),
        )
        val echoPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, echoPort)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$echoPort/sse")
            try {
                val result = client.callTool("fake.echo", emptyMap())
                assertNotNull(result)
                assertEquals("pong", result.content.filterIsInstance<TextContent>().first().text)
            } finally {
                client.close()
            }
        } finally {
            serviceWithTool.stop()
        }
    }

    @Test
    fun `start and stop can be called multiple times safely`() = runBlocking {
        service.start(host, port)
        service.start(host, port) // second call should be no-op
        service.stop()
        service.stop() // second call should be no-op
    }

    @Test
    fun `start on an occupied port reports Error`() = runBlocking {
        occupyPort().use { occupied ->
            service.start(host, occupied.localPort)
            val status = service.statusFlow.value
            assertTrue(status is McpServerStatus.Error, "Expected Error but was $status")
        }
    }

    @Test
    fun `a failed start can be retried on the same port once it frees up`() = runBlocking {
        val contestedPort = occupyPort().use { it.localPort }

        occupyPort(contestedPort).use {
            service.start(host, contestedPort)
            assertTrue(service.statusFlow.value is McpServerStatus.Error)
        }

        // The port is free again; retrying must not be blocked by the leftovers of the failed
        // attempt (notably the `running` flag it had already flipped).
        service.start(host, contestedPort)
        try {
            assertEquals(McpServerStatus.Running(host, contestedPort), service.statusFlow.value)
        } finally {
            service.stop()
        }
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `plugin tools registered by a failed start do not survive into the next start`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-failed-start"
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(testPluginId, testSessionId, FakeMcpCapablePlugin()),
        )

        occupyPort().use { occupied ->
            service.start(host, occupied.localPort)
            assertTrue(service.statusFlow.value is McpServerStatus.Error)
        }

        // No plugin instances remain loaded, so the tools the failed attempt registered must have
        // been cleared rather than carried over.
        every { pluginInstanceService.getLoadedPluginInstances() } returns emptyList()

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val toolNames = client.listTools().tools.map { it.name }
                assertFalse("com.example.test.greet" in toolNames, "Stale tool survived in $toolNames")
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `plugin tools are registered when McpCapablePlugin instance exists at server start`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-abc123"
        val fakePlugin = FakeMcpCapablePlugin()

        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(testPluginId, testSessionId, fakePlugin),
        )
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val expectedToolName = "com.example.test.greet"

                val listResult = client.listTools()
                assertNotNull(listResult)
                val toolNames = listResult.tools.map { it.name }
                assertTrue(expectedToolName in toolNames, "Expected $expectedToolName in $toolNames")

                val callResult = client.callTool(
                    expectedToolName,
                    mapOf("sessionId" to testSessionId, "name" to "World"),
                )
                assertNotNull(callResult)
                val text = callResult.content.filterIsInstance<TextContent>().first().text
                assertEquals("Hello, World!", text)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `plugin tools are registered via pluginInstanceEventFlow after server start`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-xyz789"
        val fakePlugin = FakeMcpCapablePlugin()
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 1)

        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            // Emit Ready event after server started (simulates Android device connecting later)
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

            // The event is handled asynchronously by the service's collector, so the registration
            // may not be visible the instant emit() returns. Poll until the tool appears.
            awaitToolListed("com.example.test.greet")
        } finally {
            service.stop()
        }
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `plugin tools are unregistered when Disposed event is received`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-def456"
        val fakePlugin = FakeMcpCapablePlugin()
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)

        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

            // Both events are handled asynchronously, so poll for each transition rather than
            // reading the tool list the instant emit() returns.
            awaitToolListed("com.example.test.greet")

            eventFlow.emit(PluginInstanceEvent.Disposed(testPluginId, testSessionId))

            awaitToolAbsent("com.example.test.greet")
        } finally {
            service.stop()
        }
    }

    /**
     * The service registers and unregisters plugin tools asynchronously in response to lifecycle
     * events, so a tool list read immediately after emitting an event can observe the state from
     * before the event was handled. These helpers reconnect and re-read until the tool list reaches
     * the expected state, so the tests assert on the eventual result instead of racing the handler.
     *
     * A fresh connection is opened each poll because the tool list is computed at connection time.
     */
    private suspend fun awaitToolListed(toolName: String, timeout: Duration = 5.seconds) = awaitTools(timeout, "$toolName to be listed") { toolName in it }

    private suspend fun awaitToolAbsent(toolName: String, timeout: Duration = 5.seconds) = awaitTools(timeout, "$toolName to be absent") { toolName !in it }

    private suspend fun awaitTools(timeout: Duration, description: String, predicate: (List<String>) -> Boolean) {
        var lastSeen: List<String> = emptyList()
        try {
            withTimeout(timeout) {
                while (true) {
                    val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
                    lastSeen = try {
                        client.listTools().tools.map { it.name }
                    } finally {
                        client.close()
                    }
                    if (predicate(lastSeen)) return@withTimeout
                    delay(POLL_INTERVAL_MILLIS)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for $description; last saw $lastSeen", e)
        }
    }
}

private const val POLL_INTERVAL_MILLIS = 50L

private class FakeMcpTool(
    private val name: String,
    private val response: String = "ok",
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(name = name, description = "Fake tool for testing") { _ ->
            CallToolResult(content = listOf(TextContent(response)))
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class FakeMcpCapablePlugin :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = "com.example.test.greet"
            override val description = "Greet by name"

            private val greetName by string("Name to greet", name = "name")

            override suspend fun execute(arguments: JetWhaleMcpArguments): String = "Hello, ${arguments[greetName]}!"
        },
    )
}
