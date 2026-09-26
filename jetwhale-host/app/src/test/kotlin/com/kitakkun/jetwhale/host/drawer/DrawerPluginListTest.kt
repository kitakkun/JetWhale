package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
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
    fun `a short greyed tail is listed after the enabled plugins without a fold`() = runComposeUiTest {
        showDrawer(listOf(plugin("Network", PluginAvailability.Enabled), plugin("Recorder", PluginAvailability.Disabled)))

        onNodeWithText("Network").assertExists()
        onNodeWithText("Recorder").assertExists()
        onNodeWithText("1 more").assertDoesNotExist()
    }

    @Test
    fun `a long greyed tail folds behind one row that expands in place`() = runComposeUiTest {
        showDrawer(
            listOf(
                plugin("Network", PluginAvailability.Enabled),
                plugin("Recorder", PluginAvailability.Disabled),
                plugin("Profiler", PluginAvailability.Disabled),
                plugin("Storage", PluginAvailability.Unavailable),
            ),
        )

        onNodeWithText("Recorder").assertDoesNotExist()
        onNodeWithText("3 more").performClick()

        onNodeWithText("Recorder").assertExists()
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
)
