package com.kitakkun.jetwhale.host.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpToolPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultMcpPermissionsRepositoryTest {
    @Test
    fun `the launch override lifts every denial without touching what was stored`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("jetwhale-mcp-permissions-test")
        val dataStore = PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(Dispatchers.IO)) {
            "$directory/debugger_settings.preferences_pb".toPath()
        }
        DefaultMcpPermissionsRepository(dataStore, McpPermissionOverride.None).run {
            setHostGroupAllowed(McpHostToolGroup.OBSERVE, allowed = false)
            setHostGroupAllowed(McpHostToolGroup.NAVIGATE, allowed = false)
            setPluginInspectAllowed("com.example.secret", allowed = false)
            setPluginInteractAllowed("com.example.secret", allowed = false)
            setPluginToolAllowed("com.example.secret.wipe", allowed = false)
        }
        val stored = McpPermissions(
            allowedHostGroups = emptySet(),
            pluginsDeniedInspect = setOf("com.example.secret"),
            pluginsDeniedInteract = setOf("com.example.secret"),
            deniedPluginTools = setOf("com.example.secret.wipe"),
        )

        val overridden = DefaultMcpPermissionsRepository(dataStore, McpPermissionOverride(allowAll = true)).permissionsFlow.value

        assertTrue(overridden.allows(McpToolPermission.HostGroup(McpHostToolGroup.SETTINGS_AND_SERVERS), pluginId = null))
        assertTrue(overridden.allows(McpToolPermission.PluginInspect, pluginId = "com.example.secret"))
        assertTrue(overridden.allows(McpToolPermission.PluginTool("com.example.secret.wipe"), pluginId = null))
        withTimeout(5_000) {
            DefaultMcpPermissionsRepository(dataStore, McpPermissionOverride.None).permissionsFlow.first { it == stored }
        }
    }
}
