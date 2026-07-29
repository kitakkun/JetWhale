package com.kitakkun.jetwhale.host.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
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
    // Backs LocalIsMcpCapture inside the scene's composition; the screenshot tool flips
    // it around its off-screen render so plugins can hide sensitive values from captures.
    val isMcpCapture: MutableState<Boolean>,
    // The cursor the plugin's composition currently asks for via Modifier.pointerHoverIcon. The
    // nested scene owns no window, so whoever renders it must apply this to the real one.
    val pointerIcon: State<PointerIcon>,
) {
    /**
     * Renders the scene, driving its animations from [System.nanoTime].
     *
     * The scene must never be handed a frame time that moves backwards, and it has more than one
     * renderer: the host window's draw pass and the MCP tools. The host window's frame time comes
     * from skiko, which counts from the moment its redrawer was created, while the MCP tools run
     * off-screen renders with [System.nanoTime], which counts from boot; the two are minutes and
     * days apart. A backwards jump makes a running animation see a negative elapsed time, and an
     * underdamped spring then overflows to infinity - Material3's floating text field label
     * interpolates a NaN lineHeight from it and throws out of the AWT event thread, killing the
     * host. Owning the clock here keeps every renderer on the same monotonic timeline.
     */
    fun render(canvas: Canvas) {
        composeScene.render(canvas, System.nanoTime())
    }
}

interface WindowInfoUpdater {
    val currentIntSize: IntSize
    val currentDpSize: DpSize
    fun updateWindowSize(intSize: IntSize, dpSize: DpSize)
}
