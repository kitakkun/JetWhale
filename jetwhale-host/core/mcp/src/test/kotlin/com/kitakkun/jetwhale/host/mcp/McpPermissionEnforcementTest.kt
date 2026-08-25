package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpToolPermission
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
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

        assertTrue(defaults.allows(McpToolPermission.PluginInspect, pluginId = "com.example.new"))
        assertTrue(defaults.allows(McpToolPermission.PluginInteract, pluginId = "com.example.new"))
        assertTrue(defaults.allows(McpToolPermission.PluginTool("com.example.new.listItems"), pluginId = "com.example.new"))
    }

    @Test
    fun `a call whose plugin cannot be resolved is denied rather than waved through`() {
        // Otherwise an unknown plugin id would be the way around a per-plugin denial.
        assertFalse(McpPermissions.Default.allows(McpToolPermission.PluginInspect, pluginId = null))
        assertFalse(McpPermissions.Default.allows(McpToolPermission.PluginInteract, pluginId = null))
    }

    @Test
    fun `inspect and interact are decided separately for the same plugin`() {
        // The whole point of splitting them: an agent may read a plugin's UI without being able to
        // send input to it.
        val readOnly = McpPermissions.Default.copy(pluginsDeniedInteract = setOf("com.example.secret"))

        assertTrue(readOnly.allows(McpToolPermission.PluginInspect, pluginId = "com.example.secret"))
        assertFalse(readOnly.allows(McpToolPermission.PluginInteract, pluginId = "com.example.secret"))
    }

    @Test
    fun `a plugin tool is denied by its own name, not by its plugin`() {
        // Denials are keyed by tool name so they survive the plugin having no live instance, when
        // there is no plugin id to attribute the tool to.
        val permissions = McpPermissions.Default.copy(deniedPluginTools = setOf("com.example.network.setMockRules"))

        assertFalse(permissions.allows(McpToolPermission.PluginTool("com.example.network.setMockRules"), pluginId = null))
        assertTrue(permissions.allows(McpToolPermission.PluginTool("com.example.network.listTransactions"), pluginId = null))
    }

    @Test
    fun `the launch override lifts every denial without touching what was stored`() {
        // The QA bypass has to reach tools nobody ticked a checkbox for, but only for this launch.
        val stored = McpPermissions(
            allowedHostGroups = emptySet(),
            pluginsDeniedInspect = setOf("com.example.secret"),
            pluginsDeniedInteract = setOf("com.example.secret"),
            deniedPluginTools = setOf("com.example.secret.wipe"),
        )

        val overridden = stored.allOverriddenBy(McpPermissionOverride(allowAll = true))

        assertTrue(overridden.allows(McpToolPermission.HostGroup(McpHostToolGroup.SETTINGS_AND_SERVERS), pluginId = null))
        assertTrue(overridden.allows(McpToolPermission.PluginInspect, pluginId = "com.example.secret"))
        assertTrue(overridden.allows(McpToolPermission.PluginTool("com.example.secret.wipe"), pluginId = null))
        assertEquals(stored, stored.allOverriddenBy(McpPermissionOverride.None))
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
        val message = result.content.filterIsInstance<TextContent>().first().text
        // The refusal has to be actionable: which permission blocked it, and where to change it.
        assertContains(message, "Settings & servers")
        assertContains(message, "Settings → AI Agents → Permissions")
    }

    @Test
    fun `re-allowing a group does not add its tools back to a live connection`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(allowedHostGroups = emptySet()),
        ),
        tools = setOf(ObserveCommand()),
    ) { client, permissions ->
        permissions.setHostGroupAllowed(McpHostToolGroup.OBSERVE, allowed = true)
        // Registration was skipped while it was denied, and a tool list is fixed for the life of a
        // connection, so it only comes back on the next one — the reconnect rule the docs describe.
        assertFalse("jetwhale.test.observe" in client.listTools().tools.map { it.name })
    }

    @Test
    fun `a plugin whose inspection is denied refuses the call that targets it`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(pluginsDeniedInspect = setOf("com.example.secret")),
        ),
        tools = setOf(PluginProbe("jetwhale.test.inspect", McpToolPermission.PluginInspect)),
    ) { client, _ ->
        val allowed = client.callTool("jetwhale.test.inspect", mapOf("pluginId" to "com.example.ok"))
        assertFalse(allowed.isError == true)

        val denied = client.callTool("jetwhale.test.inspect", mapOf("pluginId" to "com.example.secret"))
        assertEquals(true, denied.isError)
        val message = denied.content.filterIsInstance<TextContent>().first().text
        assertContains(message, "com.example.secret")
        // The refusal has to name which half of the plugin's UI it is about, or the user cannot tell
        // which checkbox to tick.
        assertContains(message, "reads the UI")
    }

    @Test
    fun `interaction stays denied for a plugin whose inspection is allowed`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(pluginsDeniedInteract = setOf("com.example.secret")),
        ),
        tools = setOf(
            PluginProbe("jetwhale.test.inspect", McpToolPermission.PluginInspect),
            PluginProbe("jetwhale.test.interact", McpToolPermission.PluginInteract),
        ),
    ) { client, _ ->
        val inspected = client.callTool("jetwhale.test.inspect", mapOf("pluginId" to "com.example.secret"))
        assertFalse(inspected.isError == true, "Denying interaction must not take reading away too")

        val denied = client.callTool("jetwhale.test.interact", mapOf("pluginId" to "com.example.secret"))
        assertEquals(true, denied.isError)
        assertContains(denied.content.filterIsInstance<TextContent>().first().text, "sends input to")
    }

    @Test
    fun `denying one plugin tool leaves the plugin's other tools alone`() = withServer(
        permissions = FakeMcpPermissionsRepository(
            McpPermissions.Default.copy(deniedPluginTools = setOf("jetwhale.test.mutate")),
        ),
        tools = setOf(
            PluginProbe("jetwhale.test.mutate", McpToolPermission.PluginTool("jetwhale.test.mutate")),
            PluginProbe("jetwhale.test.read", McpToolPermission.PluginTool("jetwhale.test.read")),
        ),
    ) { client, _ ->
        // A per-tool denial cannot be settled at registration either — it is stored per tool name,
        // but the tool stays listed so the refusal can explain itself.
        assertContains(client.listTools().tools.map { it.name }, "jetwhale.test.mutate")

        val denied = client.callTool("jetwhale.test.mutate", mapOf("pluginId" to "com.example.ok"))
        assertEquals(true, denied.isError)
        assertContains(denied.content.filterIsInstance<TextContent>().first().text, "not exposed to AI agents")

        val allowed = client.callTool("jetwhale.test.read", mapOf("pluginId" to "com.example.ok"))
        assertFalse(allowed.isError == true)
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

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.text("observed")
}

private class RestartCommand : HostMcpCommand() {
    override val name: String = "jetwhale.test.restart"
    override val group: McpHostToolGroup = McpHostToolGroup.SETTINGS_AND_SERVERS
    override val description: String = "Restarts."

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.text("restarted")
}

/** Stands in for the built-in UI tools: takes a `pluginId`, so the check resolves per call. */
private class PluginProbe(
    private val toolName: String,
    private val permission: McpToolPermission,
) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = toolName,
            description = "Drives a plugin",
            inputSchema = ToolSchema(
                properties = JsonObject(mapOf("pluginId" to stringProperty("The plugin."))),
                required = listOf("pluginId"),
            ),
            permission = permission,
        ) { _ ->
            CallToolResult(content = listOf(TextContent("drove it")))
        }
    }
}
