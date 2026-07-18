package com.kitakkun.jetwhale.host.model

import androidx.compose.runtime.MutableState
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
    // Backs LocalIsScreenshotCapture inside the scene's composition; the screenshot tool flips
    // it around its off-screen render so plugins can hide sensitive values from captures.
    val isScreenshotCapture: MutableState<Boolean>,
)

interface WindowInfoUpdater {
    val currentIntSize: IntSize
    val currentDpSize: DpSize
    fun updateWindowSize(intSize: IntSize, dpSize: DpSize)
}
