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
import kotlin.coroutines.cancellation.CancellationException

/**
 * @property isMcpCapture Backs LocalIsMcpCapture inside the scene's composition; the screenshot
 * tool flips it around its off-screen render so plugins can hide sensitive values from captures.
 * @property pointerIcon The cursor the plugin's composition currently asks for via
 * Modifier.pointerHoverIcon. The nested scene owns no window, so whoever renders it must apply this
 * to the real one.
 * @property failure The exception the plugin's UI threw while composing, laying out or drawing,
 * once it has; the scene is not rendered again after that, and whoever shows it should offer a
 * fresh one instead.
 */
@OptIn(InternalComposeUiApi::class)
data class PluginComposeScene(
    val composeScene: ComposeScene,
    val windowInfoUpdater: WindowInfoUpdater,
    val semanticsOwners: Set<SemanticsOwner>,
    val isMcpCapture: MutableState<Boolean>,
    val pointerIcon: State<PointerIcon>,
    val failure: MutableState<Throwable?>,
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
     *
     * A plugin that throws while rendering has its exception recorded in [failure] and rethrown;
     * a failed scene throws that exception again instead of rendering.
     */
    fun render(canvas: Canvas) {
        failure.value?.let { throw it }
        try {
            composeScene.render(canvas, System.nanoTime())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!e.isComposeSceneClosed()) failure.value = e
            throw e
        }
    }
}

/**
 * True for the benign "input/render after the scene was closed" race Compose throws when something
 * reaches a [ComposeScene] that has already been disposed (navigation, session switch, hot reload).
 */
fun Throwable.isComposeSceneClosed(): Boolean = this is IllegalStateException && message?.contains("ComposeScene is closed") == true

interface WindowInfoUpdater {
    val currentIntSize: IntSize
    val currentDpSize: DpSize
    fun updateWindowSize(intSize: IntSize, dpSize: DpSize)
}
