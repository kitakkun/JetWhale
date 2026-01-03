package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.drawer.ExpandedToolingDrawerView
import com.kitakkun.jetwhale.host.drawer.ShrunkToolingDrawerView
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ToolingDrawer(
    plugins: ImmutableList<PluginMetaData>,
    sessions: ImmutableList<DebugSession>,
    selectedSession: DebugSession?,
    selectedPluginId: String,
    onClickSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (String) -> Unit,
    onSelectSession: (DebugSession) -> Unit,
) {
    var expandMenu by remember { mutableStateOf(true) }

    AnimatedSwappableContent(
        showContent1 = expandMenu,
        content1 = {
            ExpandedToolingDrawerView(
                selectedPluginId = selectedPluginId,
                plugins = plugins,
                sessions = sessions,
                selectedSession = selectedSession,
                onClickShrinkDrawer = { expandMenu = false },
                onClickSettings = onClickSettings,
                onClickPlugin = { onClickPlugin(it.id) },
                onSelectSession = onSelectSession,
            )
        },
        content2 = {
            ShrunkToolingDrawerView(
                plugins = plugins,
                sessions = sessions,
                selectedSessionId = selectedSession?.id,
                selectedPluginId = selectedPluginId,
                onClickPlugin = onClickPlugin,
                onClickExpandMenu = { expandMenu = true },
                onClickSettings = onClickSettings,
                onClickInfo = onClickInfo,
                onSelectSession = onSelectSession,
            )
        },
    )
}
