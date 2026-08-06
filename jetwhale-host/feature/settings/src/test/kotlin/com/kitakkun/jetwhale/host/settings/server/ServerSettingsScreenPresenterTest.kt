package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.ActivateSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.CheckForUpdatesOnStartupMutationKey
import com.kitakkun.jetwhale.host.model.DebugServerSettings
import com.kitakkun.jetwhale.host.model.DebugServerSettingsMutationKey
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.host.model.DeleteSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.FollowAiOperationMutationKey
import com.kitakkun.jetwhale.host.model.GenerateSslCertificateMutationKey
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpPermissionsSnapshot
import com.kitakkun.jetwhale.host.model.McpPluginInspectPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpPluginInteractPermissionMutationKey
import com.kitakkun.jetwhale.host.model.McpPluginPermissionParams
import com.kitakkun.jetwhale.host.model.McpServerPortMutationKey
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.SignPluginTrustRegistryMutationKey
import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.SwrCachePlus
import soil.query.SwrCachePlusPolicy
import soil.query.buildMutationKey
import soil.query.compose.SwrClientProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the Debug Server page's editing rules: what counts as a change worth restarting for, which
 * ports the server could actually bind, and which source of truth each field follows.
 */
@OptIn(ExperimentalTestApi::class)
class ServerSettingsScreenPresenterTest {

    @Test
    fun `the wss fields start from the stored settings`() = runPresenter { state ->
        assertEquals("5080", state.editingDebugPortText)
        assertEquals("5443", state.editingWssPortText)
        assertTrue(state.editingWssEnabled)
        assertNull(state.debugServerSettingsError)
    }

    @Test
    fun `a wss port that no server could bind blocks Apply`() = runPresenter { state ->
        send(ServerSettingsScreenAction.ChangeWssPortText("70000"))
        assertEquals(DebugServerSettingsError.InvalidPort, state().debugServerSettingsError)
        assertTrue(state().isDebugApplyVisible, "the change is still worth showing Apply for")
        assertEquals(false, state().isDebugApplyEnabled)
    }

    @Test
    fun `the two connectors may not share a port`() = runPresenter { state ->
        send(ServerSettingsScreenAction.ChangeWssPortText("5080"))
        assertEquals(DebugServerSettingsError.PortConflict, state().debugServerSettingsError)
        assertEquals(false, state().isDebugApplyEnabled)
    }

    @Test
    fun `a port clash is tolerated while the wss connector is switched off`() = runPresenter { state ->
        send(ServerSettingsScreenAction.ChangeWssPortText("5080"))
        send(ServerSettingsScreenAction.ChangeWssEnabled(false))
        assertNull(state().debugServerSettingsError, "nothing binds the wss port while it is off")
        assertTrue(state().isDebugApplyEnabled)
    }

    @Test
    fun `an unedited screen reports no error even when the stored ports clash`() = runPresenter(
        settings = storedSettings.copy(wssPort = 5080),
    ) { state ->
        assertNull(state.debugServerSettingsError)
        assertEquals(false, state.isDebugApplyVisible)
    }

    @Test
    fun `switching the wss connector alone is a change worth applying`() = runPresenter { _ ->
        send(ServerSettingsScreenAction.ChangeWssEnabled(false))
        assertTrue(state().isDebugApplyVisible)
        assertTrue(state().isDebugApplyEnabled)
    }

    @Test
    fun `Apply hands the mutation every field the server binds from`() = runPresenter { _ ->
        send(ServerSettingsScreenAction.ChangeDebugPortText("5081"))
        send(ServerSettingsScreenAction.ChangeWssPortText("5444"))
        send(ServerSettingsScreenAction.ConfirmApplyDebugServerSettingsChange)

        assertEquals(
            DebugServerSettings(serverPort = 5081, wssPort = 5444, wssEnabled = true),
            appliedSettings,
        )
    }

    @Test
    fun `Apply is refused while the ports would clash`() = runPresenter { _ ->
        send(ServerSettingsScreenAction.ChangeWssPortText("5080"))
        send(ServerSettingsScreenAction.ConfirmApplyDebugServerSettingsChange)

        assertNull(appliedSettings, "a configuration the server could not bind must not be stored")
    }

    /**
     * A Started status carries a null wss port both when the connector is off and when it was asked
     * for but could not bind — an unloadable certificate leaves plain ws running on its own. Reading
     * the switch back off the status would turn wss off in the UI in the second case, and the next
     * Apply would persist it.
     */
    @Test
    fun `a started server that failed to bind wss leaves the switch on`() = runPresenter { _ ->
        setStatus(DebugWebSocketServerStatus.Started(host = "localhost", port = 5080, wssPort = null))

        assertTrue(state().editingWssEnabled, "the stored setting still says wss is wanted")
        assertEquals("5443", state().editingWssPortText)
        assertEquals(false, state().isDebugApplyVisible, "nothing the user did is pending")
    }

    @Test
    fun `the wss fields follow a change made outside this screen`() = runPresenter { _ ->
        setSettings(storedSettings.copy(wssPort = 5444, wssEnabled = false))

        assertEquals("5444", state().editingWssPortText)
        assertEquals(false, state().editingWssEnabled)
        assertEquals(false, state().isDebugApplyVisible, "the screen is clean again, not dirty")
    }
}

private val storedSettings = DebuggerBehaviorSettings(
    adbAutoPortMappingEnabled = true,
    checkForUpdatesOnStartup = true,
    persistData = false,
    serverPort = 5080,
    mcpServerPort = 7080,
    wssPort = 5443,
    wssEnabled = true,
    followAiOperationEnabled = true,
)

/** The handles a test body drives the running presenter through. */
private class PresenterScope(
    val state: () -> ServerSettingsScreenUiState,
    val send: (ServerSettingsScreenAction) -> Unit,
    val setStatus: (DebugWebSocketServerStatus) -> Unit,
    val setSettings: (DebuggerBehaviorSettings) -> Unit,
    val appliedSettingsProvider: () -> DebugServerSettings?,
) {
    val appliedSettings: DebugServerSettings? get() = appliedSettingsProvider()
}

@OptIn(ExperimentalTestApi::class)
private fun runPresenter(
    settings: DebuggerBehaviorSettings = storedSettings,
    body: PresenterScope.(ServerSettingsScreenUiState) -> Unit,
) = runComposeUiTest {
    var applied: DebugServerSettings? = null
    val statusFlow = MutableStateFlow<DebugWebSocketServerStatus>(DebugWebSocketServerStatus.Stopped)
    val settingsFlow = MutableStateFlow(settings)

    lateinit var uiState: ServerSettingsScreenUiState
    lateinit var channel: ScreenChannel<ServerSettingsScreenAction, Nothing>

    setContent {
        // Unconfined, so a mutation runs to completion on the caller rather than on a scope the
        // Compose test clock knows nothing about — `waitForIdle` cannot see soil's own scope.
        SwrClientProvider(SwrCachePlus(SwrCachePlusPolicy(CoroutineScope(Dispatchers.Unconfined + SupervisorJob())))) {
            channel = rememberScreenChannel()
            uiState = context(presenterContext { applied = it }) {
                serverSettingsScreenPresenter(
                    screenChannel = channel,
                    serverStatus = statusFlow.collectAsStateValue(),
                    mcpServerStatus = McpServerStatus.Stopped,
                    debuggerSettings = settingsFlow.collectAsStateValue(),
                    sslCertificates = emptyList<SslCertificateEntry>(),
                    mcpPermissionsSnapshot = McpPermissionsSnapshot(emptyPermissions, emptyList()),
                )
            }
        }
    }
    waitForIdle()

    val scope = PresenterScope(
        state = { uiState },
        send = {
            // Actions normally arrive from Root, which holds a ScreenContext.
            with(TestScreenContext) { channel.send(it) }
            waitForIdle()
        },
        setStatus = {
            statusFlow.value = it
            waitForIdle()
        },
        setSettings = {
            settingsFlow.value = it
            waitForIdle()
        },
        appliedSettingsProvider = { applied },
    )
    scope.body(uiState)
}

@Composable
private fun <T> MutableStateFlow<T>.collectAsStateValue(): T = collectAsState().value

private val emptyPermissions = McpPermissions(
    allowedHostGroups = emptySet(),
    pluginsDeniedInspect = emptySet(),
    pluginsDeniedInteract = emptySet(),
    deniedPluginTools = emptySet(),
)

private object TestScreenContext : com.kitakkun.jetwhale.host.architecture.ScreenContext

/** Every key is a no-op except the one under test, which records what Apply handed it. */
private fun presenterContext(onApply: (DebugServerSettings) -> Unit) = SettingsPresenterContext(
    appLanguageMutationKey = noop("app_language"),
    appColorSchemeMutationKey = noop("app_color_scheme"),
    adbAutoPortMappingMutationKey = noop("adb_auto_port_mapping"),
    debugServerSettingsMutationKey = object :
        DebugServerSettingsMutationKey,
        MutationKey<Unit, DebugServerSettings> by buildMutationKey(
            id = MutationId("debug_server_settings"),
            mutate = { settings: DebugServerSettings -> onApply(settings) },
        ) {},
    mcpServerPortMutationKey = object : McpServerPortMutationKey, MutationKey<Unit, Int> by noop("mcp_server_port") {},
    mcpHostGroupPermissionMutationKey = noop("mcp_host_group_permission"),
    mcpPluginInspectPermissionMutationKey = object :
        McpPluginInspectPermissionMutationKey,
        MutationKey<Unit, McpPluginPermissionParams> by noop("mcp_plugin_inspect") {},
    mcpPluginInteractPermissionMutationKey = object :
        McpPluginInteractPermissionMutationKey,
        MutationKey<Unit, McpPluginPermissionParams> by noop("mcp_plugin_interact") {},
    mcpPluginToolPermissionMutationKey = noop("mcp_plugin_tool"),
    pluginInstallMutationKey = noop("plugin_install"),
    pluginInstallFromMavenMutationKey = noop("plugin_install_maven"),
    trustPluginMutationKey = noop("trust_plugin"),
    signPluginTrustRegistryMutationKey = object :
        SignPluginTrustRegistryMutationKey,
        MutationKey<Unit, Boolean> by noop("sign_plugin_trust_registry") {},
    officialPluginInstallMutationKey = noop("official_plugin_install"),
    updateCheckMutationKey = noopReturning("update_check"),
    updateInstallMutationKey = noop("update_install"),
    checkForUpdatesOnStartupMutationKey = object :
        CheckForUpdatesOnStartupMutationKey,
        MutationKey<Unit, Boolean> by noop("check_for_updates_on_startup") {},
    followAiOperationMutationKey = object :
        FollowAiOperationMutationKey,
        MutationKey<Unit, Boolean> by noop("follow_ai_operation") {},
    hostVersionInfo = HostVersionInfo("0.0.0-test"),
    generateSslCertificateMutationKey = object :
        GenerateSslCertificateMutationKey,
        MutationKey<SslCertificateEntry, String?> by noopReturning("generate_ssl_certificate") {},
    activateSslCertificateMutationKey = object :
        ActivateSslCertificateMutationKey,
        MutationKey<Boolean, String> by noopReturning("activate_ssl_certificate") {},
    deleteSslCertificateMutationKey = object :
        DeleteSslCertificateMutationKey,
        MutationKey<Boolean, String> by noopReturning("delete_ssl_certificate") {},
    logCaptureService = NoopLogCaptureService,
)

private fun <V> noop(id: String): MutationKey<Unit, V> = buildMutationKey(id = MutationId(id), mutate = { })

private fun <T, V> noopReturning(id: String): MutationKey<T, V> = buildMutationKey(
    id = MutationId(id),
    mutate = { error("$id is not exercised by these tests") },
)

private object NoopLogCaptureService : LogCaptureService {
    override val logs = MutableStateFlow(emptyList<LogEntry>())
    override fun startCapture() = Unit
    override fun stopCapture() = Unit
    override fun clearLogs() = Unit
}
