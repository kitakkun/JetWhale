package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.ComposeScene

@OptIn(InternalComposeUiApi::class)
data class PluginComposeScene(
    val composeScene: ComposeScene,
    val windowInfoUpdater: WindowInfoUpdater,
)

interface WindowInfoUpdater {
    fun setWindowInfo(windowInfo: WindowInfo)
}
