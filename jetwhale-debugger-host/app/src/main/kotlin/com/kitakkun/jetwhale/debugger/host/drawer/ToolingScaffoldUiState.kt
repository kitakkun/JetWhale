package com.kitakkun.jetwhale.debugger.host.drawer

import com.kitakkun.jetwhale.debugger.host.model.DebugSession
import com.kitakkun.jetwhale.debugger.host.model.PluginMetaData
import kotlinx.collections.immutable.ImmutableList

data class ToolingScaffoldUiState(
    val selectedSessionId: String,
    val selectedPluginId: String,
    val sessions: ImmutableList<DebugSession>,
    val plugins: ImmutableList<PluginMetaData>,
) {
    val selectedSession: DebugSession? get() = sessions.find { it.id == selectedSessionId }
}
