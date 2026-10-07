package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.McpClientSetup
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.screen.EmptyPluginScreen
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.network.host.HttpTransaction
import com.kitakkun.jetwhale.plugins.network.host.NetworkInspectorScreenRoot
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.HostWindowSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.mouseClickThenMovePointerAway
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * The whole host window: [ToolingScaffold] with fixture state, and the plugin's screen in its content
 * slot. The host draws a plugin in a scene of its own and paints that scene into the slot; the plugin
 * here composes straight into it, which draws the same pixels.
 *
 * A window is laid out wider than the page (1.5 px per dp rather than 2), as a window on a desktop is.
 */
@OptIn(ExperimentalTestApi::class)
class HostWindowDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the window with the Network Inspector open`() = recorder.record(windowShot(page = "what-is-jetwhale", name = "overview")) { darkTheme ->
        setHostWindow(darkTheme, uiState = windowUiState(aiActivity = AGENT_CONNECTED, plugins = DRAWER_PLUGINS), overlay = {}) { NetworkInspectorContent() }
        onNodeWithText(ITEMS_URL).mouseClickThenMovePointerAway()
        onSurface()
    }

    @Test
    fun `the window with each part of the sidebar numbered`() = recorder.record(windowShot(page = "host-window", name = "annotated")) { darkTheme ->
        val callouts = mutableStateListOf<Callout>()
        setHostWindow(darkTheme, uiState = windowUiState(aiActivity = AGENT_CONNECTED, plugins = DRAWER_PLUGINS), overlay = { CalloutMarkers(callouts) }) {
            NetworkInspectorContent()
        }
        onNodeWithText(ITEMS_URL).mouseClickThenMovePointerAway()
        val sidebarEdge = JwMetrics.sidebarWidth.value * WINDOW_DENSITY
        callouts += Callout(number = 1, center = Offset(sidebarEdge, centerYOf("AI agent connected")))
        callouts += Callout(number = 2, center = Offset(sidebarEdge, centerYOf("Device Mirror")))
        callouts += Callout(number = 3, center = Offset(sidebarEdge, (centerYOf(PIXEL_9) + centerYOf(SAMPLE_APP)) / 2))
        callouts += Callout(number = 4, center = Offset(sidebarEdge, centerYOf("Compose Semantics Inspector")))
        // The footer's middle is empty, between the buttons on its left and the one on its right.
        callouts += Callout(number = 5, center = Offset(sidebarEdge / 2, (WINDOW_HEIGHT - JwMetrics.toolbarHeight / 2).value * WINDOW_DENSITY))
        callouts += Callout(number = 6, center = Offset(sidebarEdge + PLUGIN_AREA_CALLOUT_INSET.value * WINDOW_DENSITY, centerYOf("Mocks")))
        onSurface()
    }

    @Test
    fun `the first session before any plugin is installed`() = recorder.record(windowShot(page = "getting-started", name = "first-session")) { darkTheme ->
        setHostWindow(darkTheme, uiState = windowUiState(aiActivity = MCP_READY, plugins = persistentListOf()), overlay = {}) { EmptyPluginScreen() }
        onSurface()
    }

    @Test
    fun `an AI agent driving the Network Inspector`() = recorder.record(windowShot(page = "host-window", name = "ai-activity")) { darkTheme ->
        val operatedPlugins = DRAWER_PLUGINS.map { if (it.id == NETWORK_ID) it.copy(underAiControl = true) else it }
        setHostWindow(darkTheme, uiState = windowUiState(aiActivity = AGENT_OPERATING, plugins = operatedPlugins.toPersistentList()), overlay = {}) {
            NetworkInspectorContent()
        }
        onNodeWithText(AGENT_OPERATING.operatingToolShortName.orEmpty()).mouseClickThenMovePointerAway()
        onSurface()
    }

    @Test
    fun `the plugin list with its folded groups open`() = recorder.record(
        DocsShot(page = "host-window", name = "plugin-groups", surfaceSize = DpSize(JwMetrics.sidebarWidth, 470.dp), density = 2f, displayWidth = JwMetrics.sidebarWidth.value.roundToInt()),
    ) { darkTheme ->
        setContent {
            HostWindowSurface(darkTheme = darkTheme) {
                ToolingDrawerWithoutActions(windowUiState(aiActivity = AGENT_CONNECTED, plugins = DRAWER_PLUGINS))
            }
        }
        onNodeWithText("1 not in this app").mouseClickThenMovePointerAway()
        onSurface()
    }
}

private fun windowShot(page: String, name: String) = DocsShot(
    page = page,
    name = name,
    surfaceSize = DpSize((WINDOW_DISPLAY_WIDTH * 2 / WINDOW_DENSITY).dp, WINDOW_HEIGHT),
    density = WINDOW_DENSITY,
    displayWidth = WINDOW_DISPLAY_WIDTH,
)

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.centerYOf(text: String): Float = onNodeWithText(text).fetchSemanticsNode().boundsInRoot.center.y

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.setHostWindow(
    darkTheme: Boolean,
    uiState: ToolingScaffoldUiState,
    overlay: @Composable () -> Unit,
    pluginContent: @Composable () -> Unit,
) {
    setContent {
        HostWindowSurface(darkTheme = darkTheme) {
            Box(Modifier.fillMaxSize()) {
                ToolingScaffold(
                    uiState = uiState,
                    snackbarHostState = remember { JwSnackbarHostState() },
                    onClickSettings = {},
                    onClickPluginSettings = {},
                    onClickInfo = {},
                    onClickPlugin = {},
                    onClickInactivePlugin = {},
                    onOpenMcpTools = {},
                    onOpenAllMcpTools = {},
                    onClickPopout = {},
                    isPoppedOut = { false },
                    onClickBringBack = {},
                    onSelectSession = {},
                    onSetPluginEnabled = { _, _ -> },
                    onResizeSidebar = {},
                    onSidebarResizeFinished = {},
                    onFollowAiOperationChange = {},
                    onOpenMcpSettings = {},
                    content = { JwSurface(Modifier.fillMaxSize()) { pluginContent() } },
                )
                overlay()
            }
        }
    }
}

@Composable
private fun ToolingDrawerWithoutActions(uiState: ToolingScaffoldUiState) {
    ExpandedToolingDrawerView(
        selectedPluginId = uiState.selectedPluginId,
        plugins = uiState.plugins,
        hasFailedJars = false,
        selectedSession = uiState.selectedSession,
        sessions = uiState.sessions,
        aiActivity = uiState.aiActivity,
        width = uiState.sidebarWidth,
        onResize = {},
        onResizeFinished = {},
        onFollowAiOperationChange = {},
        onOpenMcpSettings = {},
        onClickShrinkDrawer = {},
        onClickSettings = {},
        onClickPluginSettings = {},
        onClickInfo = {},
        onOpenMcpTools = {},
        onOpenAllMcpTools = {},
        onClickPlugin = {},
        onClickInactivePlugin = {},
        onSelectSession = {},
        onClickPopout = {},
        isPoppedOut = { false },
        onClickBringBack = {},
        onSetPluginEnabled = { _, _ -> },
    )
}

/** The Network Inspector as the plugin area shows it, its list given half the width. */
@Composable
private fun NetworkInspectorContent() {
    val storage = remember { InMemoryPluginStorage(mapOf("traffic.splitPosition" to "0.5")) }
    CompositionLocalProvider(LocalJetWhalePluginStorage provides storage) {
        NetworkInspectorScreenRoot(
            transactions = TRANSACTIONS,
            mockRules = emptyList(),
            mockingEnabled = true,
            onClearTransactions = {},
            onToggleMocking = {},
            onMockRulesChanged = {},
        )
    }
}

/** A numbered marker on the window, centered on [center], in pixels of the captured image. */
private class Callout(val number: Int, val center: Offset)

@Composable
private fun CalloutMarkers(callouts: List<Callout>) {
    callouts.forEach { callout ->
        JwSurface(
            color = CALLOUT_COLOR,
            contentColor = Color.White,
            shape = CircleShape,
            border = BorderStroke(CALLOUT_BORDER, Color.White),
            modifier = Modifier
                .offset {
                    val radius = (CALLOUT_SIZE / 2).roundToPx()
                    IntOffset(callout.center.x.roundToInt() - radius, callout.center.y.roundToInt() - radius)
                }
                .size(CALLOUT_SIZE),
        ) {
            JwText(
                text = callout.number.toString(),
                style = JwTheme.textStyles.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun windowUiState(aiActivity: AiActivityUiState, plugins: ImmutableList<DrawerPluginItemUiState>) = ToolingScaffoldUiState(
    selectedSessionId = SESSION.id,
    selectedPluginId = NETWORK_ID,
    sessions = persistentListOf(SESSION),
    plugins = plugins,
    hasFailedJars = false,
    aiActivity = aiActivity,
    sidebarWidth = JwMetrics.sidebarWidth,
)

private const val WINDOW_DISPLAY_WIDTH = 688

private const val WINDOW_DENSITY = 1.5f

private val WINDOW_HEIGHT = 460.dp

/** How far into the plugin area its marker sits: past the Traffic and Mocks tabs. */
private val PLUGIN_AREA_CALLOUT_INSET = 220.dp

private val CALLOUT_SIZE = 24.dp

private val CALLOUT_BORDER = 2.dp

private val CALLOUT_COLOR = Color(0xFFE5484D)

private const val PIXEL_9 = "Pixel 9"

private const val SAMPLE_APP = "Sample App"

private const val NETWORK_ID = "com.kitakkun.jetwhale.network"

private const val ITEMS_URL = "https://example.com/api/items?page=1&size=20"

private val MCP_READY = AiActivityUiState.Idle.copy(mcpServer = McpServerAvailability.Ready(McpClientSetup.forServer(host = "localhost", port = 7080)))

private val AGENT_CONNECTED = MCP_READY.copy(isAgentConnected = true, isFollowModeOn = true)

private val AGENT_OPERATING = AGENT_CONNECTED.copy(
    operatingToolName = "com.kitakkun.jetwhale.network.listTransactions",
    operatingToolShortName = "network.listTransactions",
    operatingPluginName = "Network Inspector",
    operatingAppName = SAMPLE_APP,
)

private val SESSION = DebugSession(
    id = "4b7e21c9-0d5a-4f63-a8e2-6c1f9d3b7a40",
    name = SAMPLE_APP,
    isActive = true,
    transportSecurity = SessionTransportSecurity.LOOPBACK,
    installedPlugins = persistentListOf(
        JetWhalePluginInfo(pluginId = NETWORK_ID, pluginVersion = "1.0.0"),
        JetWhalePluginInfo(pluginId = "com.kitakkun.jetwhale.semantics", pluginVersion = "1.1.0"),
        JetWhalePluginInfo(pluginId = "com.kitakkun.jetwhale.nav3", pluginVersion = "1.0.0"),
        JetWhalePluginInfo(pluginId = "com.kitakkun.jetwhale.actions", pluginVersion = "1.0.0"),
    ),
    appName = SAMPLE_APP,
    deviceId = "emulator-5554",
    deviceName = PIXEL_9,
)

/**
 * Every official plugin installed: the Device Mirror needs no app, Debug Actions is switched off, and
 * the app has no Storage Inspector agent.
 */
private val DRAWER_PLUGINS = persistentListOf(
    drawerPlugin(name = "Device Mirror", id = "com.kitakkun.jetwhale.mirror", iconName = "mirror", availability = PluginAvailability.Enabled, needsApp = false),
    drawerPlugin(name = "Network Inspector", id = NETWORK_ID, iconName = "network", availability = PluginAvailability.Enabled, needsApp = true),
    drawerPlugin(name = "Compose Semantics Inspector", id = "com.kitakkun.jetwhale.semantics", iconName = "node_tree", availability = PluginAvailability.Enabled, needsApp = true),
    drawerPlugin(name = "Nav3 Navigator", id = "com.kitakkun.jetwhale.nav3", iconName = "nav3", availability = PluginAvailability.Enabled, needsApp = true),
    drawerPlugin(name = "Debug Actions", id = "com.kitakkun.jetwhale.actions", iconName = "actions", availability = PluginAvailability.Disabled, needsApp = true),
    drawerPlugin(name = "Storage Inspector", id = "com.kitakkun.jetwhale.storage", iconName = "storage", availability = PluginAvailability.Unavailable, needsApp = true),
)

/** An official plugin's row, with the icons its jar declares in its manifest. */
private fun drawerPlugin(name: String, id: String, iconName: String, availability: PluginAvailability, needsApp: Boolean) = DrawerPluginItemUiState(
    name = name,
    id = id,
    activeIconResource = pluginIcon("icons/${iconName}_filled.svg"),
    inactiveIconResource = pluginIcon("icons/${iconName}_outlined.svg"),
    pluginAvailability = availability,
    underAiControl = false,
    exposesMcpTools = availability == PluginAvailability.Enabled,
    isHeadless = false,
    needsApp = needsApp,
)

private fun pluginIcon(path: String) = PluginIconResource(checkNotNull(HostWindowDocsScreenshots::class.java.classLoader.getResource(path)) { "$path is not on the classpath" })

private val JSON_HEADERS = mapOf("Content-Type" to listOf("application/json; charset=utf-8"))

/** Oldest first, as the agent reports them; the list shows the newest on top. */
private val TRANSACTIONS = listOf(
    transaction(txId = "tx-1", method = "GET", url = "https://example.com/api/session", status = 200 to "OK", body = """{"signedIn":true}""", durationMs = 64),
    transaction(
        txId = "tx-2",
        method = "GET",
        url = ITEMS_URL,
        status = 200 to "OK",
        body = """{"items":[{"id":41,"name":"Blue mug","price":12.5},{"id":42,"name":"Notebook","price":4.0}],"page":1,"total":2}""",
        durationMs = 142,
    ),
    transaction(txId = "tx-3", method = "POST", url = "https://example.com/api/cart", status = 201 to "Created", body = """{"cartId":7,"items":1}""", durationMs = 210),
    transaction(txId = "tx-4", method = "GET", url = "https://example.com/api/recommendations", status = 503 to "Service Unavailable", body = """{"error":"maintenance"}""", durationMs = 300)
        .let { it.copy(response = it.response?.copy(fromMock = true)) },
    transaction(txId = "tx-5", method = "GET", url = "https://example.com/api/profile", status = 401 to "Unauthorized", body = """{"error":"token expired"}""", durationMs = 55),
    transaction(txId = "tx-6", method = "PUT", url = "https://example.com/api/cart/7", status = 200 to "OK", body = """{"cartId":7,"items":2}""", durationMs = 61),
    transaction(txId = "tx-7", method = "GET", url = "https://example.com/api/orders?status=open", status = 200 to "OK", body = """{"orders":[]}""", durationMs = 97),
)

private fun transaction(txId: String, method: String, url: String, status: Pair<Int, String>, body: String, durationMs: Long) = HttpTransaction(
    request = CapturedHttpRequest(txId = txId, method = method, url = url, timestampMs = 0),
    response = CapturedHttpResponse(
        txId = txId,
        statusCode = status.first,
        statusDescription = status.second,
        headers = JSON_HEADERS,
        body = body,
        durationMs = durationMs,
    ),
)
