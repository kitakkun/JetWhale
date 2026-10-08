package com.kitakkun.jetwhale.host.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.McpClientSetup
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.settings.plugin.OfficialPluginUiState
import com.kitakkun.jetwhale.host.settings.plugin.PluginSettingsScreen
import com.kitakkun.jetwhale.host.settings.plugin.PluginSettingsScreenUiState
import com.kitakkun.jetwhale.host.settings.server.McpPermissionsUiState
import com.kitakkun.jetwhale.host.settings.server.McpPluginPermissionUiState
import com.kitakkun.jetwhale.host.settings.server.McpPluginToolUiState
import com.kitakkun.jetwhale.host.settings.server.ServerSettingsScreen
import com.kitakkun.jetwhale.host.settings.server.ServerSettingsScreenUiState
import com.kitakkun.jetwhale.host.settings.server.ServerState
import com.kitakkun.jetwhale.plugins.network.host.NetworkHostPluginFactory
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsScreenshot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsScreenshotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.HostWindowSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.mouseClickThenMovePointerAway
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test

/**
 * Pages of the settings dialog, inside [SettingsScreenScaffold] with the page's section open, as the
 * dialog opens on a page. The dialog takes 80% of the window it opens in, so the window here is a
 * quarter larger than the captured dialog.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsDocsScreenshots {
    private val recorder = DocsScreenshotRecorder.fromImagesDirectorySystemProperty()

    @Test
    fun `the official plugins on the Add Plugins page`() = recorder.record(dialogScreenshot(guidePage = "getting-started", name = "official-plugins", windowHeightDp = 700)) { darkTheme ->
        setSettingsDialogContent(darkTheme, SettingsScreenPage.AddPlugins) { page ->
            PluginSettingsScreen(
                page = page,
                uiState = PluginSettingsScreenUiState(
                    plugins = persistentListOf(),
                    officialPlugins = OfficialPluginCatalog.plugins
                        .map { OfficialPluginUiState(plugin = it, isInstalled = it.pluginId == NETWORK_PLUGIN_ID, installJob = null) }
                        .toImmutableList(),
                    failedJars = persistentListOf(),
                    untrustedJarPaths = persistentListOf(),
                    signPluginTrustRegistry = false,
                    installJobs = persistentListOf(),
                    isAddingFromFile = false,
                    addFromFileError = null,
                ),
                onClickAddPlugin = {},
                onApproveUntrustedJar = {},
                onClickInstallFromMaven = {},
                onClickInstallOfficialPlugin = {},
                onCancelInstall = {},
                onRetryInstall = {},
                onDismissInstall = {},
                onChangeSignPluginTrustRegistry = {},
            )
        }
        onNodeWithTag(SETTINGS_DIALOG_TAG)
    }

    @Test
    fun `the MCP Server page with the client setups`() = recorder.record(dialogScreenshot(guidePage = "mcp-server", name = "setup", windowHeightDp = 680)) { darkTheme ->
        setSettingsDialogContent(darkTheme, SettingsScreenPage.McpServer) { page -> ServerSettings(page, serverSettingsUiState(McpPermissionsUiState(DEFAULT_ALLOWED_HOST_GROUPS, emptyList(), isOverriddenForLaunch = false))) }
        onNodeWithTag(SETTINGS_DIALOG_TAG)
    }

    @Test
    fun `the permissions tree with a plugin open`() = recorder.record(dialogScreenshot(guidePage = "mcp-server", name = "permissions", windowHeightDp = 680)) { darkTheme ->
        val permissions = McpPermissionsUiState(
            allowedHostGroups = DEFAULT_ALLOWED_HOST_GROUPS,
            plugins = listOf(
                McpPluginPermissionUiState(pluginId = NETWORK_PLUGIN_ID, displayName = "Network Inspector", inspectAllowed = true, interactAllowed = true, tools = networkToolPermissions()),
                McpPluginPermissionUiState(pluginId = "com.kitakkun.jetwhale.semantics", displayName = "Compose Semantics Inspector", inspectAllowed = true, interactAllowed = true, tools = emptyList()),
                McpPluginPermissionUiState(pluginId = "com.kitakkun.jetwhale.mirror", displayName = "Device Mirror", inspectAllowed = true, interactAllowed = false, tools = emptyList()),
            ),
            isOverriddenForLaunch = false,
        )
        setSettingsDialogContent(darkTheme, SettingsScreenPage.McpPermissions) { page -> ServerSettings(page, serverSettingsUiState(permissions)) }
        onNodeWithText("Network Inspector").mouseClickThenMovePointerAway()
        onNodeWithTag(SETTINGS_DIALOG_TAG)
    }
}

private const val SETTINGS_DIALOG_TAG = "settings-dialog"

private const val NETWORK_PLUGIN_ID = "com.kitakkun.jetwhale.network"

private val DIALOG_WIDTH = 688.dp

private fun dialogScreenshot(guidePage: String, name: String, windowHeightDp: Int) = DocsScreenshot(
    page = guidePage,
    name = name,
    surfaceSize = DpSize(DIALOG_WIDTH * WINDOW_PER_DIALOG, windowHeightDp.dp),
    density = 2f,
    displayWidthCssPx = DIALOG_WIDTH.value.toInt(),
)

/** The window the dialog is laid out in, per unit of dialog: the dialog takes 80% of it. */
private const val WINDOW_PER_DIALOG = 1.25f

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.setSettingsDialogContent(darkTheme: Boolean, page: SettingsScreenPage, pageContent: @Composable (SettingsScreenPage) -> Unit) {
    setContent {
        HostWindowSurface(darkTheme = darkTheme) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SettingsScreenScaffold(
                    uiState = SettingsScreenScaffoldUiState(selectedPage = page, expandedSections = setOf(page.section)),
                    onClickClose = {},
                    onSelectPage = {},
                    onToggleSection = {},
                    modifier = Modifier.testTag(SETTINGS_DIALOG_TAG),
                    content = pageContent,
                )
            }
        }
    }
}

@Composable
private fun ServerSettings(page: SettingsScreenPage, uiState: ServerSettingsScreenUiState) {
    ServerSettingsScreen(
        page = page,
        uiState = uiState,
        onDebugPortTextChange = {},
        onWssPortTextChange = {},
        onWssEnabledChange = {},
        onApplyDebugServerSettingsChange = {},
        onConfirmApplyDebugServerSettingsChange = {},
        onDismissApplyDebugServerSettingsDialog = {},
        onMcpPortTextChange = {},
        onApplyMcpPortChange = {},
        onConfirmApplyMcpPortChange = {},
        onDismissApplyMcpPortDialog = {},
        onClickOpenMcpGuide = {},
        onSetHostGroupAllowed = { _, _ -> },
        onSetPluginInspectAllowed = { _, _ -> },
        onSetPluginInteractAllowed = { _, _ -> },
        onSetPluginToolAllowed = { _, _ -> },
        onAddCertificate = {},
        onSetActiveCertificate = {},
        onDeleteCertificate = {},
        onShowCertificateDetail = {},
        onDismissCertificateDetailDialog = {},
    )
}

/** The groups a fresh install allows: looking around, but no installing and no server restarts. */
private val DEFAULT_ALLOWED_HOST_GROUPS = setOf(McpHostToolGroup.OBSERVE, McpHostToolGroup.NAVIGATE)

/** Both servers running on their default ports, nothing being edited. */
private fun serverSettingsUiState(permissions: McpPermissionsUiState): ServerSettingsScreenUiState {
    val mcpSetup = McpClientSetup.forServer(host = "localhost", port = 7080)
    return ServerSettingsScreenUiState(
        debugServerState = ServerState.Running(host = "0.0.0.0", port = 5080, wssPort = 5443),
        mcpServerState = ServerState.Running(host = "localhost", port = 7080),
        editingDebugPortText = "5080",
        editingWssPortText = "5443",
        editingWssEnabled = true,
        debugServerSettingsError = null,
        editingMcpPortText = "7080",
        mcpClaudeCodeCommand = mcpSetup.claudeCodeCommand,
        mcpJsonConfig = mcpSetup.jsonConfig,
        mcpPermissions = permissions,
        isDebugApplyVisible = false,
        isMcpApplyVisible = false,
        isDebugApplyEnabled = false,
        isMcpApplyEnabled = false,
        isDebugRetry = false,
        isMcpRetry = false,
        showDebugApplyConfirmDialog = false,
        showMcpApplyConfirmDialog = false,
        certificates = emptyList(),
        certificateDetailDialogEntry = null,
    )
}

/**
 * The tools the Network Inspector publishes, each allowed: the plugin is created and bound as the
 * host does before it reads them.
 */
@OptIn(ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class)
private fun networkToolPermissions(): List<McpPluginToolUiState> {
    val pluginScope = CoroutineScope(Job())
    val plugin = NetworkHostPluginFactory().createPlugin()
    plugin.bindPluginScope(pluginScope)
    plugin.bindStorage(InMemoryPluginStorage(emptyMap()))
    val tools = (plugin as JetWhaleMcpCapablePlugin).mcpCommands.map { McpPluginToolUiState(toolName = it.name, allowed = true) }
    pluginScope.cancel()
    return tools
}
