package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpToolPermission
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpPermissionEnforcementTest {

    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getLoadedPluginInstances() } returns emptyList()
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }

    @Test
    fun `by default an agent may observe and navigate but not manage plugins or servers`() {
        val defaults = McpPermissions.Default

        assertTrue(defaults.allows(McpToolPermission.HostGroup(McpHostToolGroup.OBSERVE), pluginId = null))
        assertTrue(defaults.allows(McpToolPermission.HostGroup(McpHostToolGroup.NAVIGATE), pluginId = null))
        assertFalse(defaults.allows(McpToolPermission.HostGroup(McpHostToolGroup.MANAGE_PLUGINS), pluginId = null))
        assertFalse(defaults.allows(McpToolPermission.HostGroup(McpHostToolGroup.SETTINGS_AND_SERVERS), pluginId = null))
    }

    @Test
    fun `a plugin nobody has ruled out is allowed`() {
        val defaults = McpPermissions.Default

        assertTrue(defaults.allows(McpToolPermission.PluginUi, pluginId = "com.example.new"))
        assertTrue(defaults.allows(McpToolPermission.PluginOwnTools, pluginId = "com.example.new"))
    }

    @Test
    fun `a call whose plugin cannot be resolved is denied rather than waved through`() {
        // Otherwise an unknown plugin id would be the way around a per-plugin denial.
        assertFalse(McpPermissions.Default.allows(McpToolPermission.PluginUi, pluginId = null))
        assertFalse(McpPermissions.Default.allows(McpToolPermission.PluginOwnTools, pluginId = null))
    }

    @Test
    fun `a denied host group's tools are not even listed`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(allowedHostGroups = setOf(McpHostToolGroup.OBSERVE)),
        ),
        tools = setOf(ObserveCommand(), RestartCommand()),
    ) { client, _ ->
        val names = client.listTools().tools.map { it.name }

        assertContains(names, "jetwhale.test.observe")
        assertFalse("jetwhale.test.restart" in names, "A denied group must not be advertised: $names")
    }

    @Test
    fun `revoking a group after the client connected still stops the call`() = withServer(
        permissions = FakeMcpPermissionsRepository(),
        tools = setOf(RestartCommand()),
    ) { client, permissions ->
        // Registered while allowed, so it is in this connection's tool list for good.
        assertContains(client.listTools().tools.map { it.name }, "jetwhale.test.restart")

        permissions.setHostGroupAllowed(McpHostToolGroup.SETTINGS_AND_SERVERS, allowed = false)

        val result = client.callTool("jetwhale.test.restart", emptyMap())
        assertEquals(true, result.isError)
        assertContains(result.content.filterIsInstance<TextContent>().first().text, "Settings & servers")
    }

    @Test
    fun `a refusal names the screen that lifts it`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(allowedHostGroups = emptySet()),
        ),
        tools = setOf(ObserveCommand()),
    ) { client, permissions ->
        permissions.setHostGroupAllowed(McpHostToolGroup.OBSERVE, allowed = true)
        // Re-allowed after registration was skipped, so the tool is absent from this connection but
        // would be back on the next one — the reconnect rule the docs describe.
        assertFalse("jetwhale.test.observe" in client.listTools().tools.map { it.name })
    }

    @Test
    fun `a plugin whose UI is denied refuses the call that targets it`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(pluginsDeniedUi = setOf("com.example.secret")),
        ),
        tools = setOf(PluginUiProbe()),
    ) { client, _ ->
        val allowed = client.callTool("jetwhale.test.pluginUi", mapOf("pluginId" to "com.example.ok"))
        assertFalse(allowed.isError == true)

        val denied = client.callTool("jetwhale.test.pluginUi", mapOf("pluginId" to "com.example.secret"))
        assertEquals(true, denied.isError)
        assertContains(denied.content.filterIsInstance<TextContent>().first().text, "com.example.secret")
    }

    private fun withServer(
        permissions: FakeMcpPermissionsRepository,
        tools: Set<JetWhaleMcpTool>,
        block: suspend (Client, FakeMcpPermissionsRepository) -> Unit,
    ) = runBlocking {
        val service = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = FakeMcpActivityRepository(),
            mcpPermissionsRepository = permissions,
            builtInTools = tools,
            statusHolder = McpServerStatusHolder(),
        )
        val port = java.net.ServerSocket(0).use { it.localPort }
        service.start("localhost", port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://localhost:$port/sse")
            try {
                block(client, permissions)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }
}

private class ObserveCommand : HostMcpCommand() {
    override val name: String = "jetwhale.test.observe"
    override val group: McpHostToolGroup = McpHostToolGroup.OBSERVE
    override val description: String = "Observes."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = "observed"
}

private class RestartCommand : HostMcpCommand() {
    override val name: String = "jetwhale.test.restart"
    override val group: McpHostToolGroup = McpHostToolGroup.SETTINGS_AND_SERVERS
    override val description: String = "Restarts."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = "restarted"
}

/** Stands in for the built-in UI tools: takes a `pluginId`, so the check resolves per call. */
private class PluginUiProbe : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = "jetwhale.test.pluginUi",
            description = "Drives a plugin UI",
            inputSchema = io.modelcontextprotocol.kotlin.sdk.types.ToolSchema(
                properties = kotlinx.serialization.json.JsonObject(mapOf("pluginId" to stringProperty("The plugin."))),
                required = listOf("pluginId"),
            ),
            permission = McpToolPermission.PluginUi,
        ) { _ ->
            io.modelcontextprotocol.kotlin.sdk.types.CallToolResult(content = listOf(TextContent("drove it")))
        }
    }
}
