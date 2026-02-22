package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize

@OptIn(InternalComposeUiApi::class)
data class PluginComposeScene(
    val composeScene: ComposeScene,
    val windowInfoUpdater: WindowInfoUpdater,
)

interface WindowInfoUpdater {
    fun updateWindowSize(intSize: IntSize, dpSize: DpSize)
}
