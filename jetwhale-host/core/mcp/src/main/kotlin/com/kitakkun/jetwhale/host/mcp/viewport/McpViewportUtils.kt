package com.kitakkun.jetwhale.host.mcp.viewport

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
    scene.composeScene.render(Canvas(ImageBitmap(size.width, size.height)), System.nanoTime())
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
