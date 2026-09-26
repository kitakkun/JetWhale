package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DrawerPluginListTest {
    private val session = DebugSession(
        id = "app-1",
        name = "Sample",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
        appName = "Sample",
        deviceId = "device-1",
        deviceName = "Pixel 9",
    )

    @Test
    fun `a few disabled plugins are listed open under their fold after the enabled ones`() = runComposeUiTest {
        showDrawer(listOf(plugin("Network", PluginAvailability.Enabled), plugin("Recorder", PluginAvailability.Disabled)))

        onNodeWithText("Network").assertExists()
        onNodeWithText("1 disabled").assertExists()
        onNodeWithText("Recorder").assertExists()
    }

    @Test
    fun `many disabled plugins start folded and open in place`() = runComposeUiTest {
        showDrawer(
            listOf(
                plugin("Network", PluginAvailability.Enabled),
                plugin("Recorder", PluginAvailability.Disabled),
                plugin("Profiler", PluginAvailability.Disabled),
                plugin("Tracer", PluginAvailability.Disabled),
            ),
        )

        onNodeWithText("Recorder").assertDoesNotExist()
        onNodeWithText("3 disabled").performClick()

        onNodeWithText("Recorder").assertExists()
    }

    @Test
    fun `plugins the app doesn't include sit in their own fold, folded to begin with`() = runComposeUiTest {
        showDrawer(
            listOf(
                plugin("Network", PluginAvailability.Enabled),
                plugin("Recorder", PluginAvailability.Disabled),
                plugin("Storage", PluginAvailability.Unavailable),
            ),
        )

        onNodeWithText("Recorder").assertExists()
        onNodeWithText("Storage").assertDoesNotExist()
        onNodeWithText("1 not in this app").performClick()

        onNodeWithText("Storage").assertExists()
    }

    @Test
    fun `clicking a greyed plugin asks to explain it instead of opening it`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val explained = mutableListOf<String>()
        showDrawer(
            listOf(plugin("Network", PluginAvailability.Enabled), plugin("Recorder", PluginAvailability.Disabled)),
            onClickPlugin = { opened += it.name },
            onClickInactivePlugin = { explained += it.name },
        )

        onNodeWithText("Recorder").performClick()

        assertEquals(listOf("Recorder"), explained)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `an app with no plugins says so instead of leaving the area blank`() = runComposeUiTest {
        showDrawer(listOf(plugin("Device Mirror", PluginAvailability.Enabled).copy(needsApp = false)))

        onNodeWithText("Device Mirror").assertExists()
        onNodeWithText("This app has no plugins yet.").assertExists()
    }

    @Test
    fun `an app with only plugins it doesn't include shows their fold, not the empty message`() = runComposeUiTest {
        showDrawer(listOf(plugin("Storage", PluginAvailability.Unavailable)))

        onNodeWithText("1 not in this app").assertExists()
        onNodeWithText("This app has no plugins yet.").assertDoesNotExist()
    }

    private fun ComposeUiTest.showDrawer(
        plugins: List<DrawerPluginItemUiState>,
        onClickPlugin: (DrawerPluginItemUiState) -> Unit = {},
        onClickInactivePlugin: (DrawerPluginItemUiState) -> Unit = {},
    ) {
        setContent {
            JwTheme(darkTheme = false) {
                ExpandedToolingDrawerView(
                    selectedPluginId = "",
                    plugins = plugins.toImmutableList(),
                    hasFailedJars = false,
                    selectedSession = session,
                    sessions = persistentListOf(session),
                    aiActivity = AiActivityUiState.Idle,
                    width = JwMetrics.sidebarWidth,
                    onResize = {},
                    onResizeFinished = {},
                    onClickShrinkDrawer = {},
                    onClickSettings = {},
                    onClickPluginSettings = {},
                    onClickInfo = {},
                    onOpenMcpTools = {},
                    onOpenAllMcpTools = {},
                    onClickPlugin = onClickPlugin,
                    onClickInactivePlugin = onClickInactivePlugin,
                    onSelectSession = {},
                    onClickPopout = {},
                    isPoppedOut = { false },
                    onClickBringBack = {},
                    onSetPluginEnabled = { _, _ -> },
                )
            }
        }
    }
}

private fun plugin(name: String, availability: PluginAvailability) = DrawerPluginItemUiState(
    name = name,
    id = "com.example.${name.lowercase()}",
    activeIconResource = null,
    inactiveIconResource = null,
    pluginAvailability = availability,
    underAiControl = false,
    exposesMcpTools = false,
    isHeadless = false,
    needsApp = true,
    failureMessage = null,
)
