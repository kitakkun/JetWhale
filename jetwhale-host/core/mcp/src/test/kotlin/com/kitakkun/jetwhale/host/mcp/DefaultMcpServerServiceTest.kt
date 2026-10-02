package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.McpToolPermission
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        mcpPermissionsRepository = FakeMcpPermissionsRepository(),
        builtInTools = emptySet(),
        statusHolder = McpServerStatusHolder(),
    )

    private val host = "localhost"
    private val port = ServerSocket(0).use(ServerSocket::getLocalPort)

    @Test
    fun `listTools returns all registered built-in tools`() = runBlocking {
        val serviceWithTools = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(
                FakeMcpTool("fake.toolA"),
                FakeMcpTool("fake.toolB"),
                FakeMcpTool("fake.toolC"),
            ),
            statusHolder = McpServerStatusHolder(),
        )
        val toolsPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTools.start(host, toolsPort)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$toolsPort/sse")
            try {
                assertEquals(setOf("fake.toolA", "fake.toolB", "fake.toolC"), client.listTools().tools.map(Tool::name).toSet())
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(FakeMcpTool("fake.echo", response = "pong")),
            statusHolder = McpServerStatusHolder(),
        )
        val echoPort = ServerSocket(0).use(ServerSocket::getLocalPort)
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
        service.start(host, port)
        assertEquals(McpServerStatus.Running(host, port), service.statusFlow.value)

        service.stop()
        service.stop()
        assertEquals(McpServerStatus.Stopped, service.statusFlow.value)
    }

    @Test
    fun `start on an occupied port reports Error`() = runBlocking {
        occupyPort().use { occupied ->
            service.start(host, occupied.localPort)
            val status = service.statusFlow.value
            assertTrue(status is McpServerStatus.Error, "Expected Error but was $status")
        }
    }

    /**
     * Holds [port] (0 picks a free one) on the loopback address specifically. A wildcard-bound
     * `ServerSocket(0)` would not do: BSD-derived systems let a SO_REUSEADDR socket bind
     * 127.0.0.1:p over an existing 0.0.0.0:p, so the server would start just fine and the test
     * would silently stop testing the failure path.
     */
    private fun occupyPort(port: Int = 0): ServerSocket = ServerSocket(port, 50, InetAddress.getByName(host))

    @Test
    fun `a failed start can be retried on the same port once it frees up`() = runBlocking {
        val contestedPort = occupyPort().use(ServerSocket::getLocalPort)

        occupyPort(contestedPort).use {
            service.start(host, contestedPort)
            assertTrue(service.statusFlow.value is McpServerStatus.Error)
        }

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

        every { pluginInstanceService.getLoadedPluginInstances() } returns emptyList()

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val toolNames = client.listTools().tools.map(Tool::name)
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
                val toolNames = listResult.tools.map(Tool::name)
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
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 1)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-xyz789"
        val fakePlugin = FakeMcpCapablePlugin()
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

            awaitCapableFor(testSessionId) { testPluginId in it }

            assertTrue("com.example.test.greet" in servedToolNames())
        } finally {
            service.stop()
        }
    }

    @Test
    fun `plugin tools are unregistered when Disposed event is received`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-def456"
        val fakePlugin = FakeMcpCapablePlugin()
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

            awaitCapableFor(testSessionId) { testPluginId in it }
            assertTrue("com.example.test.greet" in servedToolNames())

            eventFlow.emit(PluginInstanceEvent.Disposed(testPluginId, testSessionId))

            awaitCapableFor(testSessionId) { testPluginId !in it }
            assertFalse("com.example.test.greet" in servedToolNames())
        } finally {
            service.stop()
        }
    }

    /**
     * Waits for the service to have handled a plugin lifecycle event: the registry publishes the
     * capable plugins as its last step in both registering and unregistering a plugin's tools, so
     * once the flow reports [condition] the tool list a new connection is served is settled too.
     */
    private suspend fun awaitCapableFor(sessionId: String, condition: (Set<String>) -> Boolean) {
        withTimeout(5.seconds) {
            service.mcpCapablePluginsFlow.first { condition(it.pluginIdsFor(sessionId)) }
        }
    }

    /** The tool list a freshly connected MCP client is served; it is computed at connection time. */
    private suspend fun servedToolNames(): List<String> {
        val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
        return try {
            client.listTools().tools.map(Tool::name)
        } finally {
            client.close()
        }
    }

    @Test
    fun `every MCP-capable plugin in a session is reported as capable`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        val sessionId = "test-session-multi"
        val pluginA = "com.example.a"
        every { pluginInstanceService.getPluginInstanceForSession(pluginA, sessionId) } returns
            FakeMcpCapablePlugin(toolName = "com.example.a.greet")
        val pluginB = "com.example.b"
        every { pluginInstanceService.getPluginInstanceForSession(pluginB, sessionId) } returns
            FakeMcpCapablePlugin(toolName = "com.example.b.greet")

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(pluginA, sessionId))
            eventFlow.emit(PluginInstanceEvent.Ready(pluginB, sessionId))

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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(
                FakeMcpTool("fake.observed") {
                    runningDuringCall = mcpActivityRepository.activityFlow.value.runningInvocations.map(McpToolInvocation::toolName)
                },
            ),
            statusHolder = McpServerStatusHolder(),
        )
        val observedPort = ServerSocket(0).use(ServerSocket::getLocalPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(FakeMcpTool("fake.targeted")),
            statusHolder = McpServerStatusHolder(),
        )
        val targetedPort = ServerSocket(0).use(ServerSocket::getLocalPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(FailingMcpTool("fake.failing")),
            statusHolder = McpServerStatusHolder(),
        )
        val failingPort = ServerSocket(0).use(ServerSocket::getLocalPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(FakeMcpTool("fake.recorded")),
            statusHolder = McpServerStatusHolder(),
        )
        val recordedPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTool.start(host, recordedPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(MediaMcpTool("fake.captured")),
            statusHolder = McpServerStatusHolder(),
        )
        val capturedPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTool.start(host, capturedPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(ErrorResultMcpTool("fake.rejected")),
            statusHolder = McpServerStatusHolder(),
        )
        val rejectedPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTool.start(host, rejectedPort)
        val record = try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$rejectedPort/sse")
            try {
                val result = client.callTool("fake.rejected", emptyMap())
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
        assertEquals("no such element", record.response)
    }

    @Test
    fun `a structured response is recorded alongside the text content`() = runBlocking {
        val serviceWithTool = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = mcpActivityRepository,
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(StructuredMcpTool("fake.structured")),
            statusHolder = McpServerStatusHolder(),
        )
        val structuredPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTool.start(host, structuredPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(MirroredStructuredMcpTool("fake.mirrored")),
            statusHolder = McpServerStatusHolder(),
        )
        val mirroredPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTool.start(host, mirroredPort)
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
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            builtInTools = setOf(FailingMcpTool("fake.failing")),
            statusHolder = McpServerStatusHolder(),
        )
        val failingPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        serviceWithTool.start(host, failingPort)
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

        val invocation = mcpActivityRepository.recordedInvocations.single()
        assertEquals("com.example.test.greet", invocation.toolName)
        assertEquals(testPluginId, invocation.pluginId)
        assertEquals(testSessionId, invocation.sessionId)
    }

    @Test
    fun `a plugin error result reaches the agent flagged as an error`() = runBlocking {
        val callResult = callPluginTool(FixedResultPlugin(JetWhaleMcpResult.error("no widget with id: 7")), sessionArguments)

        assertEquals(true, callResult.isError)
        assertEquals("no widget with id: 7", callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @Test
    fun `a failure a plugin throws reaches the agent flagged as an error`() = runBlocking {
        val callResult = callPluginTool(RejectingPlugin(), sessionArguments)

        assertEquals(true, callResult.isError)
        assertEquals("unknown widget id", callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @Test
    fun `a plugin structured result arrives as structuredContent repeated as text`() = runBlocking {
        val payload = buildJsonObject {
            put("width", 120)
            put("height", 40)
        }
        val callResult = callPluginTool(FixedResultPlugin(JetWhaleMcpResult.json(payload)), sessionArguments)

        assertEquals(false, callResult.isError)
        assertEquals(payload, callResult.structuredContent)
        assertEquals(payload.toString(), callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @Test
    fun `a plugin image arrives Base64-encoded as an image block`() = runBlocking {
        val callResult = callPluginTool(FixedResultPlugin(JetWhaleMcpResult.image(data = byteArrayOf(1, 2, 3), mimeType = "image/png")), sessionArguments)

        val image = callResult.content.filterIsInstance<ImageContent>().single()
        assertEquals("AQID", image.data)
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun `a declared output schema reaches the agent on the listed tool`() = runBlocking {
        val tool = describePluginTool(DeclaredOutputPlugin(answersThroughOutput = true))

        val outputSchema = assertNotNull(tool.outputSchema, "Expected an output schema on $tool")
        assertEquals(listOf("widthPx", "heightPx", "label"), outputSchema.properties?.keys?.toList())
        assertEquals(listOf("widthPx", "heightPx"), outputSchema.required)
    }

    @Test
    fun `a command that declares no output advertises none`() = runBlocking {
        assertNull(describePluginTool(FakeMcpCapablePlugin(PLUGIN_TOOL)).outputSchema)
    }

    @Test
    fun `a declared output answers with structured content`() = runBlocking {
        val callResult = callPluginTool(DeclaredOutputPlugin(answersThroughOutput = true), sessionArguments)

        assertEquals(false, callResult.isError)
        assertEquals(
            buildJsonObject {
                put("widthPx", 120)
                put("heightPx", 40)
            },
            callResult.structuredContent,
        )
    }

    @Test
    fun `a plugin answer that bypasses its declared output reaches the agent as a failure`() = runBlocking {
        val callResult = callPluginTool(DeclaredOutputPlugin(answersThroughOutput = false), sessionArguments)

        assertEquals(true, callResult.isError)
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, "declares an output but answered without it")
    }

    @Test
    fun `a plugin tool called without a sessionId is told the sessions it runs in`() = runBlocking {
        val callResult = callPluginTool(FakeMcpCapablePlugin(PLUGIN_TOOL), emptyMap())

        assertEquals(true, callResult.isError)
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, "'sessionId' is required")
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, PLUGIN_SESSION)
    }

    @Test
    fun `a plugin tool called for a session that does not have it names that session`() = runBlocking {
        val callResult = callPluginTool(FakeMcpCapablePlugin(PLUGIN_TOOL), mapOf("sessionId" to "session-gone"))

        assertEquals(true, callResult.isError)
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, "session 'session-gone'")
    }

    private val sessionArguments = mapOf("sessionId" to PLUGIN_SESSION)

    /** Starts the service with [plugin] loaded in one session, and reads its tool off the tool list. */
    private suspend fun describePluginTool(plugin: JetWhaleHostPlugin): Tool {
        loadInOneSession(plugin)
        service.start(host, port)
        return try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.listTools().tools.single { it.name == PLUGIN_TOOL }
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    /** Starts the service with [plugin] loaded in one session, and calls its tool with [arguments]. */
    private suspend fun callPluginTool(plugin: JetWhaleHostPlugin, arguments: Map<String, String>): CallToolResult {
        loadInOneSession(plugin)
        service.start(host, port)
        return try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.callTool(PLUGIN_TOOL, arguments)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    private fun loadInOneSession(plugin: JetWhaleHostPlugin) {
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(LoadedPluginInstance(PLUGIN_ID, PLUGIN_SESSION, plugin))
        every { pluginInstanceService.getPluginInstanceForSession(PLUGIN_ID, PLUGIN_SESSION) } returns plugin
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

private class FakeMcpTool(
    private val name: String,
    private val response: String = "ok",
    private val onExecute: () -> Unit = {},
) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Fake tool for testing", inputSchema = ToolSchema(), permission = McpToolPermission.Unrestricted) { _ ->
            onExecute()
            CallToolResult(content = listOf(TextContent(response)))
        }
    }
}

/** Returns a text block alongside a binary one, which history must name rather than inline. */
private class MediaMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Returns text and an image", inputSchema = ToolSchema(), permission = McpToolPermission.Unrestricted) { _ ->
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
        registrar.addTool(name = name, description = "Always reports an error result", inputSchema = ToolSchema(), permission = McpToolPermission.Unrestricted) { _ ->
            errorResult("no such element")
        }
    }
}

/** Answers with both prose and a machine-readable payload, as a tool with an output schema does. */
private class StructuredMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Returns structured content", inputSchema = ToolSchema(), permission = McpToolPermission.Unrestricted) { _ ->
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

/** Repeats its structured payload as text, as [JetWhaleMcpResult.json] does for clients that read only text. */
private class MirroredStructuredMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        val payload = buildJsonObject { put("width", 120) }
        registrar.addTool(name = name, description = "Repeats its payload as text", inputSchema = ToolSchema(), permission = McpToolPermission.Unrestricted) { _ ->
            CallToolResult(content = listOf(TextContent(payload.toString())), structuredContent = payload)
        }
    }
}

private class FailingMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(name = name, description = "Always throws", inputSchema = ToolSchema(), permission = McpToolPermission.Unrestricted) { _ ->
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

private const val PLUGIN_ID = "com.example.test"
private const val PLUGIN_SESSION = "test-session-result"
private const val PLUGIN_TOOL = "com.example.test.tool"

private class FixedResultPlugin(private val result: JetWhaleMcpResult) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = PLUGIN_TOOL
            override val description = "Answers with a fixed result"

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = result
        },
    )
}

private class RejectingPlugin :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpTextCommand() {
            override val name = PLUGIN_TOOL
            override val description = "Always rejects the call"

            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = throw JetWhaleMcpArgumentException("unknown widget id")
        },
    )
}

@Serializable
private data class WidgetMeasurement(val widthPx: Int, val heightPx: Int, val label: String = "")

private class DeclaredOutputPlugin(private val answersThroughOutput: Boolean) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = PLUGIN_TOOL
            override val description = "Measures the selected widget"

            private val measurement = serializableOutput<WidgetMeasurement>()

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = when {
                answersThroughOutput -> measurement.result(WidgetMeasurement(widthPx = 120, heightPx = 40))
                else -> JetWhaleMcpResult.json(buildJsonObject { put("widthPx", "wide") })
            }
        },
    )
}
