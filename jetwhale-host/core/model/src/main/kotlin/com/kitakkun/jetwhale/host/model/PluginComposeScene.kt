package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize

@OptIn(InternalComposeUiApi::class)
data class PluginComposeScene(
    val composeScene: ComposeScene,
    val windowInfoUpdater: WindowInfoUpdater,
    val semanticsOwners: Set<SemanticsOwner>,
)

interface WindowInfoUpdater {
    val currentIntSize: IntSize
    val currentDpSize: DpSize
    fun updateWindowSize(intSize: IntSize, dpSize: DpSize)
}
