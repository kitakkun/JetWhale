package com.kitakkun.jetwhale.host.mcp.viewport

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene

data class McpViewport(
    val size: IntSize,
    val density: Density,
)

internal fun IntSize.isValidForViewport(): Boolean = width > 0 && height > 0

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
