package com.kitakkun.jetwhale.host.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
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
    // The cursor the plugin's composition currently asks for via Modifier.pointerHoverIcon. The
    // nested scene owns no window, so whoever renders it must apply this to the real one.
    val pointerIcon: State<PointerIcon>,
)

interface WindowInfoUpdater {
    val currentIntSize: IntSize
    val currentDpSize: DpSize
    fun updateWindowSize(intSize: IntSize, dpSize: DpSize)
}
