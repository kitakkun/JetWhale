package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DefaultMcpServerServiceTest {

    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getLoadedPluginInstances() } returns emptyList()
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }

    private val mcpActivityRepository = FakeMcpActivityRepository()

    private val service = DefaultMcpServerService(
        pluginInstanceService = pluginInstanceService,
        mcpActivityRepository = mcpActivityRepository,
        builtInTools = emptySet(),
        statusHolder = McpServerStatusHolder(),
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
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(
                FakeMcpTool("fake.toolA"),
                FakeMcpTool("fake.toolB"),
                FakeMcpTool("fake.toolC"),
            ),
            statusHolder = McpServerStatusHolder(),
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
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(FakeMcpTool("fake.echo", response = "pong")),
            statusHolder = McpServerStatusHolder(),
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

    @Test
    fun `every MCP-capable plugin in a session is reported as capable`() = runBlocking {
        // Regression guard: the drawer's "exposes MCP tools" badge reads mcpCapablePluginsFlow, and
        // every plugin that registers tools must appear there — not just the first one. This mirrors
        // a session that has two MCP-capable plugins installed at once.
        val sessionId = "test-session-multi"
        val pluginA = "com.example.a"
        val pluginB = "com.example.b"
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)

        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        every { pluginInstanceService.getPluginInstanceForSession(pluginA, sessionId) } returns
            FakeMcpCapablePlugin(toolName = "com.example.a.greet")
        every { pluginInstanceService.getPluginInstanceForSession(pluginB, sessionId) } returns
            FakeMcpCapablePlugin(toolName = "com.example.b.greet")

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(pluginA, sessionId))
            eventFlow.emit(PluginInstanceEvent.Ready(pluginB, sessionId))

            // Registration is handled asynchronously, so wait for the flow to settle on both.
            val capable = withTimeout(5.seconds) {
                service.mcpCapablePluginsFlow.first { it.pluginIdsFor(sessionId).size == 2 }
            }
            assertEquals(setOf(pluginA, pluginB), capable.pluginIdsFor(sessionId))
        } finally {
            service.stop()
        }
    }

    @Test
    fun `a tool call is reported as running while it executes and cleared afterwards`() = runBlocking {
        var runningDuringCall: List<String> = emptyList()
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(
                FakeMcpTool("fake.observed") {
                    runningDuringCall = mcpActivityRepository.activityFlow.value.runningInvocations.map { it.toolName }
                },
            ),
            statusHolder = McpServerStatusHolder(),
        )
        val observedPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, observedPort)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$observedPort/sse")
            try {
                client.callTool("fake.observed", emptyMap())
            } finally {
                client.close()
            }
        } finally {
            serviceWithTool.stop()
        }

        assertEquals(listOf("fake.observed"), runningDuringCall)
        assertTrue(mcpActivityRepository.activityFlow.value.runningInvocations.isEmpty())
    }

    @Test
    fun `a tool call records the plugin and session it targets`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(FakeMcpTool("fake.targeted")),
            statusHolder = McpServerStatusHolder(),
        )
        val targetedPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, targetedPort)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$targetedPort/sse")
            try {
                client.callTool(
                    "fake.targeted",
                    mapOf("pluginId" to "com.example.plugin", "sessionId" to "session-1"),
                )
            } finally {
                client.close()
            }
        } finally {
            serviceWithTool.stop()
        }

        val invocation = mcpActivityRepository.recordedInvocations.single()
        assertEquals("fake.targeted", invocation.toolName)
        assertEquals("com.example.plugin", invocation.pluginId)
        assertEquals("session-1", invocation.sessionId)
    }

    @Test
    fun `a failing tool call still stops being reported as running`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(FailingMcpTool("fake.failing")),
            statusHolder = McpServerStatusHolder(),
        )
        val failingPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, failingPort)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$failingPort/sse")
            try {
                runCatching { client.callTool("fake.failing", emptyMap()) }
            } finally {
                client.close()
            }
        } finally {
            serviceWithTool.stop()
        }

        assertTrue(mcpActivityRepository.activityFlow.value.runningInvocations.isEmpty())
    }

    @Test
    fun `a completed tool call is added to the recent-call history`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(FakeMcpTool("fake.recorded")),
            statusHolder = McpServerStatusHolder(),
        )
        val recordedPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, recordedPort)
        // Stopping the server clears recorded activity, so the history has to be read while it runs.
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$recordedPort/sse")
            try {
                client.callTool(
                    "fake.recorded",
                    mapOf("pluginId" to "com.example.plugin", "sessionId" to "session-1"),
                )
            } finally {
                client.close()
            }
            mcpActivityRepository.activityFlow.value.recentCalls.single()
        } finally {
            serviceWithTool.stop()
        }

        assertEquals("fake.recorded", record.toolName)
        assertEquals("com.example.plugin", record.pluginId)
        assertEquals("session-1", record.sessionId)
        assertTrue(record.succeeded)
        assertEquals("ok", record.response)
    }

    @Test
    fun `a non-text response block is recorded as a placeholder instead of its payload`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(MediaMcpTool("fake.captured")),
            statusHolder = McpServerStatusHolder(),
        )
        val capturedPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, capturedPort)
        // Stopping the server clears recorded activity, so the history has to be read while it runs.
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$capturedPort/sse")
            try {
                client.callTool("fake.captured", emptyMap())
            } finally {
                client.close()
            }
            mcpActivityRepository.activityFlow.value.recentCalls.single()
        } finally {
            serviceWithTool.stop()
        }

        assertEquals("captured\n<image>", record.response)
    }

    @Test
    fun `a tool call that reports an error without throwing is recorded as a failure`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(ErrorResultMcpTool("fake.rejected")),
            statusHolder = McpServerStatusHolder(),
        )
        val rejectedPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, rejectedPort)
        // Stopping the server clears recorded activity, so the history has to be read while it runs.
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$rejectedPort/sse")
            try {
                val result = client.callTool("fake.rejected", emptyMap())
                // The handler returns normally, so nothing but `isError` marks this as a failure.
                assertEquals(true, result.isError)
            } finally {
                client.close()
            }
            mcpActivityRepository.activityFlow.value.recentCalls.single()
        } finally {
            serviceWithTool.stop()
        }

        assertEquals("fake.rejected", record.toolName)
        assertFalse(record.succeeded)
        assertEquals("""{"error":"no such element"}""", record.response)
    }

    @Test
    fun `a structured response is recorded alongside the text content`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(StructuredMcpTool("fake.structured")),
            statusHolder = McpServerStatusHolder(),
        )
        val structuredPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, structuredPort)
        // Stopping the server clears recorded activity, so the history has to be read while it runs.
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$structuredPort/sse")
            try {
                client.callTool("fake.structured", emptyMap())
            } finally {
                client.close()
            }
            mcpActivityRepository.activityFlow.value.recentCalls.single()
        } finally {
            serviceWithTool.stop()
        }

        assertTrue(record.succeeded)
        assertEquals("measured", record.response.lineSequence().first())
        assertTrue("\"width\":120" in record.response, "Structured payload missing from ${record.response}")
    }

    @Test
    fun `a structured payload mirrored into text is recorded once`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(MirroredStructuredMcpTool("fake.mirrored")),
            statusHolder = McpServerStatusHolder(),
        )
        val mirroredPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, mirroredPort)
        // Stopping the server clears recorded activity, so the history has to be read while it runs.
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$mirroredPort/sse")
            try {
                client.callTool("fake.mirrored", emptyMap())
            } finally {
                client.close()
            }
            mcpActivityRepository.activityFlow.value.recentCalls.single()
        } finally {
            serviceWithTool.stop()
        }

        assertEquals("""{"width":120}""", record.response)
    }

    @Test
    fun `a throwing tool call is recorded in history as a failure`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            builtInTools = setOf(FailingMcpTool("fake.failing")),
            statusHolder = McpServerStatusHolder(),
        )
        val failingPort = java.net.ServerSocket(0).use { it.localPort }
        serviceWithTool.start(host, failingPort)
        // Stopping the server clears recorded activity, so the history has to be read while it runs.
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$failingPort/sse")
            try {
                runCatching { client.callTool("fake.failing", emptyMap()) }
            } finally {
                client.close()
            }
            mcpActivityRepository.activityFlow.value.recentCalls.single()
        } finally {
            serviceWithTool.stop()
        }

        assertEquals("fake.failing", record.toolName)
        assertFalse(record.succeeded)
        assertEquals("boom", record.response)
    }

    @Test
    fun `a plugin tool call is attributed to the plugin that owns it`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-attribution"
        val fakePlugin = FakeMcpCapablePlugin()

        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(testPluginId, testSessionId, fakePlugin),
        )
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.callTool(
                    "com.example.test.greet",
                    mapOf("sessionId" to testSessionId, "name" to "World"),
                )
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }

        // Plugin tool schemas carry no pluginId, so this only works if attribution is resolved
        // through the registry rather than read off the arguments.
        val invocation = mcpActivityRepository.recordedInvocations.single()
        assertEquals("com.example.test.greet", invocation.toolName)
        assertEquals(testPluginId, invocation.pluginId)
        assertEquals(testSessionId, invocation.sessionId)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a plugin error result reaches the agent flagged as an error`() = runBlocking {
        val toolName = "com.example.test.fails"
        val callResult = callPluginTool(FixedResultPlugin(toolName, JetWhaleMcpResult.error("no widget with id: 7")), toolName)

        assertEquals(true, callResult.isError)
        assertEquals("no widget with id: 7", callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a caller mistake a plugin throws reaches the agent flagged as an error`() = runBlocking {
        val toolName = "com.example.test.rejects"
        val callResult = callPluginTool(RejectingMcpCapablePlugin(toolName), toolName)

        // Thrown rather than returned, and it still has to arrive as a failure the agent can correct.
        assertEquals(true, callResult.isError)
        assertEquals("unknown widget id", callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a plugin structured result arrives as structuredContent`() = runBlocking {
        val toolName = "com.example.test.measures"
        val payload = buildJsonObject {
            put("width", 120)
            put("height", 40)
        }
        val callResult = callPluginTool(FixedResultPlugin(toolName, JetWhaleMcpResult.json(payload)), toolName)

        assertEquals(payload, callResult.structuredContent)
        assertEquals(false, callResult.isError)
        // The same payload is repeated as text, for agents that read nothing else.
        assertEquals(payload.toString(), callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a plugin image result arrives as an image block`() = runBlocking {
        val toolName = "com.example.test.captures"
        val callResult = callPluginTool(
            FixedResultPlugin(toolName, JetWhaleMcpResult.image(base64Data = "AAAA", mimeType = "image/png")),
            toolName,
        )

        val image = callResult.content.filterIsInstance<ImageContent>().single()
        assertEquals("AAAA", image.data)
        assertEquals("image/png", image.mimeType)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a declared output schema reaches the agent on the listed tool`() = runBlocking {
        val toolName = "com.example.test.measures"
        val tool = describePluginTool(DeclaredOutputPlugin(toolName), toolName)

        val outputSchema = assertNotNull(tool.outputSchema, "Expected an output schema on $tool")
        assertEquals("object", outputSchema.type)
        assertEquals(listOf("width", "height", "label"), outputSchema.properties?.keys?.toList())
        // Only the properties without a default have to be present in the answer.
        assertEquals(listOf("width", "height"), outputSchema.required)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a command that declares no output advertises none`() = runBlocking {
        val toolName = "com.example.test.greet"
        val tool = describePluginTool(FakeMcpCapablePlugin(toolName), toolName)

        // A text-only tool must keep saying nothing about its output rather than promising a shape.
        assertNull(tool.outputSchema)
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `a declared output answers with structured content shaped like its schema`() = runBlocking {
        val toolName = "com.example.test.measures"
        val callResult = callPluginTool(DeclaredOutputPlugin(toolName), toolName)

        assertEquals(false, callResult.isError)
        assertEquals(
            buildJsonObject {
                put("width", 120)
                put("height", 40)
            },
            callResult.structuredContent,
        )
    }

    /**
     * Starts the service with [plugin] loaded for one session and reads [toolName] off the tool
     * list, which is where an agent learns what the tool takes and answers with.
     */
    @OptIn(ExperimentalJetWhaleApi::class)
    private suspend fun describePluginTool(plugin: JetWhaleHostPlugin, toolName: String): Tool {
        val pluginId = "com.example.test"
        val sessionId = "test-session-schema"
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(pluginId, sessionId, plugin),
        )
        every { pluginInstanceService.getPluginInstanceForSession(pluginId, sessionId) } returns plugin

        service.start(host, port)
        return try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.listTools().tools.single { it.name == toolName }
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    /**
     * Starts the service with [plugin] loaded for one session and calls [toolName] on it. Every
     * result-shape test needs the same registration dance, and only the answer is interesting.
     */
    @OptIn(ExperimentalJetWhaleApi::class)
    private suspend fun callPluginTool(plugin: JetWhaleHostPlugin, toolName: String): CallToolResult {
        val pluginId = "com.example.test"
        val sessionId = "test-session-result"
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(pluginId, sessionId, plugin),
        )
        every { pluginInstanceService.getPluginInstanceForSession(pluginId, sessionId) } returns plugin

        service.start(host, port)
        return try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.callTool(toolName, mapOf("sessionId" to sessionId))
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @Test
    fun `a connected client is counted until it disconnects`() = runBlocking<Unit> {
        service.start(host, port)
        try {
            val httpClient = HttpClient(CIO) { install(SSE) }
            val client = httpClient.mcpSse("http://$host:$port/sse")
            try {
                assertEquals(1, mcpActivityRepository.activityFlow.value.connectedClientCount)
            } finally {
                client.close()
                // Closing only the MCP client leaves the underlying socket open, so the server
                // would never see the disconnect.
                httpClient.close()
            }
            // The server only learns of the departure on its next liveness probe.
            withTimeout(30.seconds) {
                mcpActivityRepository.activityFlow.first { it.connectedClientCount == 0 }
            }
        } finally {
            service.stop()
        }
    }
}

private const val POLL_INTERVAL_MILLIS = 50L

private class FakeMcpTool(
    private val name: String,
    private val response: String = "ok",
    private val onExecute: () -> Unit = {},
) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Fake tool for testing", inputSchema = ToolSchema()) { _ ->
            onExecute()
            CallToolResult(content = listOf(TextContent(response)))
        }
    }
}

/** Returns a text block alongside a binary one, which history must name rather than inline. */
private class MediaMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Returns text and an image", inputSchema = ToolSchema()) { _ ->
            CallToolResult(
                content = listOf(
                    TextContent("captured"),
                    ImageContent(data = "AAAA", mimeType = "image/png"),
                ),
            )
        }
    }
}

/** Reports a tool-level failure the way the protocol prefers: a normal return flagged `isError`. */
private class ErrorResultMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Always reports an error result", inputSchema = ToolSchema()) { _ ->
            errorResult("no such element")
        }
    }
}

/** Answers with both prose and a machine-readable payload, as a tool with an output schema does. */
private class StructuredMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Returns structured content", inputSchema = ToolSchema()) { _ ->
            CallToolResult(
                content = listOf(TextContent("measured")),
                structuredContent = buildJsonObject {
                    put("width", 120)
                    put("height", 40)
                },
            )
        }
    }
}

/**
 * Repeats its structured payload as a text block, which is what the protocol asks a structured tool
 * to do for clients that read nothing else — and what [JetWhaleMcpResult.json] produces.
 */
@OptIn(ExperimentalJetWhaleApi::class)
private class MirroredStructuredMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        val payload = buildJsonObject { put("width", 120) }
        registrar.addTool(name = name, description = "Repeats its payload as text", inputSchema = ToolSchema()) { _ ->
            CallToolResult(content = listOf(TextContent(payload.toString())), structuredContent = payload)
        }
    }
}

private class FailingMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Always throws", inputSchema = ToolSchema()) { _ ->
            error("boom")
        }
    }
}

private class FakeMcpCapablePlugin(private val toolName: String = "com.example.test.greet") :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpTextCommand() {
            override val name = toolName
            override val description = "Greet by name"

            private val greetName by string("Name to greet", name = "name")

            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = "Hello, ${arguments[greetName]}!"
        },
    )
}

/** A plugin whose single tool always answers with [result], to check how it reaches the wire. */
@OptIn(ExperimentalJetWhaleApi::class)
private class FixedResultPlugin(private val toolName: String, private val result: JetWhaleMcpResult) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Answers with a fixed result"

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = result
        },
    )
}

@Serializable
private data class WidgetMeasurement(val width: Int, val height: Int, val label: String = "")

/** A plugin whose single tool declares the `@Serializable` shape of its answer. */
@OptIn(ExperimentalJetWhaleApi::class)
private class DeclaredOutputPlugin(private val toolName: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Measures the selected widget"

            private val measurement = serializableOutput<WidgetMeasurement>()

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = measurement.result(WidgetMeasurement(width = 120, height = 40))
        },
    )
}

/** A plugin whose single tool rejects every call the way a command reports a caller mistake. */
@OptIn(ExperimentalJetWhaleApi::class)
private class RejectingMcpCapablePlugin(private val toolName: String) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpTextCommand() {
            override val name = toolName
            override val description = "Always rejects the call"

            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = throw JetWhaleMcpArgumentException("unknown widget id")
        },
    )
}
