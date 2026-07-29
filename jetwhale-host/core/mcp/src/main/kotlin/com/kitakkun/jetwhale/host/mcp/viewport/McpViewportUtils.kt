package com.kitakkun.jetwhale.host.mcp.viewport

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene

data class McpViewport(
    val size: IntSize,
    val density: Density,
)

internal fun IntSize.isValidForViewport(): Boolean = width > 0 && height > 0

/**
 * Resolves the viewport for a scene using the current size, falling back to WindowInfoUpdater
 * or a 1280×720 default, then applies the viewport and renders to flush pending recompositions.
 *
 * Must be called on the UI thread (Dispatchers.Main).
 * Call this before reading semantics/bounds to ensure they are up to date.
 */
@OptIn(InternalComposeUiApi::class)
internal fun ensureSceneRendered(scene: PluginComposeScene) {
    val currentSize = runCatching { scene.composeScene.size }.getOrNull()
    val size = currentSize?.takeIf { it.isValidForViewport() }
        ?: scene.windowInfoUpdater.currentIntSize.takeIf { it.isValidForViewport() }
        ?: IntSize(1280, 720)
    val viewport = McpViewport(size = size, density = scene.composeScene.density)
    applyViewport(scene, viewport)
    scene.render(Canvas(ImageBitmap(size.width, size.height)))
}

@OptIn(InternalComposeUiApi::class)
internal fun applyViewport(scene: PluginComposeScene, viewport: McpViewport) {
    // Keep ComposeScene and WindowInfo in sync; pointer/semantics coordinates depend on both.
    try {
        scene.composeScene.density = viewport.density
        scene.composeScene.size = viewport.size
    } catch (_: IllegalStateException) {
        // May happen during dispose/close; ignore to avoid crashing MCP calls.
    }

    val dpSize = with(viewport.density) {
        DpSize(viewport.size.width.toDp(), viewport.size.height.toDp())
    }
    scene.windowInfoUpdater.updateWindowSize(intSize = viewport.size, dpSize = dpSize)
}

/**
 * Applies [viewport] for the duration of [block], then puts the scene back on the size, density and
 * window info it had.
 *
 * The scene is the live, on-screen one and the interactive renderer only pushes its own size and
 * density when the host window resizes, so a viewport left applied here would keep the visible
 * plugin UI rendering at it and every later capture would inherit it.
 *
 * Must be called on the UI thread (Dispatchers.Main).
 */
internal fun <T> withScopedViewport(
    scene: PluginComposeScene,
    viewport: McpViewport,
    block: () -> T,
): T {
    val previous = readSceneViewportState(scene)
    applyViewport(scene, viewport)
    try {
        return block()
    } finally {
        restoreSceneViewportState(scene, previous)
    }
}

/**
 * The scene state [applyViewport] overwrites.
 *
 * [composeSceneSize] is nullable because a ComposeScene may have no size of its own, which means
 * "measure against the window instead of a fixed constraint" — a state worth putting back verbatim.
 */
private class SceneViewportState(
    val composeSceneSize: IntSize?,
    val density: Density,
    val windowIntSize: IntSize,
    val windowDpSize: DpSize,
)

/** Null when the scene cannot be read, i.e. it is being disposed and has nothing left to restore. */
@OptIn(InternalComposeUiApi::class)
private fun readSceneViewportState(scene: PluginComposeScene): SceneViewportState? = runCatching {
    SceneViewportState(
        composeSceneSize = scene.composeScene.size,
        density = scene.composeScene.density,
        windowIntSize = scene.windowInfoUpdater.currentIntSize,
        windowDpSize = scene.windowInfoUpdater.currentDpSize,
    )
}.getOrNull()

@OptIn(InternalComposeUiApi::class)
private fun restoreSceneViewportState(scene: PluginComposeScene, state: SceneViewportState?) {
    if (state == null) return
    try {
        scene.composeScene.density = state.density
        scene.composeScene.size = state.composeSceneSize
    } catch (_: IllegalStateException) {
        // May happen during dispose/close; ignore to avoid crashing MCP calls.
    }
    scene.windowInfoUpdater.updateWindowSize(intSize = state.windowIntSize, dpSize = state.windowDpSize)
    // The scene density and the window info override are snapshot state. Flush the restore so the
    // next interactive render observes the original viewport immediately rather than lagging a
    // frame behind on the capture's one.
    Snapshot.sendApplyNotifications()
}
