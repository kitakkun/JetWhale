package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.window.FrameWindowScope

/**
 * Registers this window's Compose roots for as long as the call stays composed.
 *
 * Desktop has no equivalent of Android's process-wide root callback, so the probe is scoped to a
 * window and goes inside it:
 *
 * ```kotlin
 * singleWindowApplication {
 *     JetWhaleSemanticsProbe()
 *     App()
 * }
 * ```
 *
 * [ComposeWindow.semanticsOwners] is snapshot-backed, so reading it here re-runs this composable
 * whenever the window gains or loses a root — a `Dialog` or a `Popup` rendered inside the window
 * appears and disappears on its own, with no further calls needed. A window of its own (a separate
 * `Window { }`) is a separate composition and needs its own call.
 */
@ExperimentalComposeUiApi
@Composable
fun FrameWindowScope.JetWhaleSemanticsProbe() {
    val window = window
    val density = LocalDensity.current.density
    // Snapshotted into a list so the DisposableEffect key compares by content: the collection
    // itself is the same instance across changes, which would never re-key the effect.
    val owners = window.semanticsOwners.toList()

    DisposableEffect(window, owners, density) {
        val registrations = owners.mapIndexed { index, owner ->
            ComposeNodeSourceRegistry.register(owner.toNodeSource(window, density, index))
        }
        onDispose { registrations.forEach(AutoCloseable::close) }
    }
}

private fun SemanticsOwner.toNodeSource(window: ComposeWindow, density: Float, index: Int): SemanticsOwnerNodeSource {
    val title = window.title.ifEmpty { "Compose window" }
    return SemanticsOwnerNodeSource(
        sourceId = "compose-root-${System.identityHashCode(this).toString(16)}",
        owner = { this },
        // The collection carries no names, so the extra roots — a dialog or popup layer — are
        // distinguished by position rather than guessed at.
        label = { if (index == 0) title else "$title / layer $index" },
        density = { density },
        windowOffset = { window.composeSurfaceOffsetOnScreen() },
        uiThread = SwingComposeUiThread,
    )
}

/**
 * Where the window's Compose surface sits on screen, so in-window bounds can be reported in screen
 * coordinates. Reading it needs the window to be on screen and to be read on the AWT thread — which
 * is where captures run — and it is treated as unknown rather than fatal when it is not.
 */
private fun ComposeWindow.composeSurfaceOffsetOnScreen(): Offset {
    if (!isShowing) return Offset.Zero
    val location = runCatching { contentPane.locationOnScreen }.getOrNull() ?: return Offset.Zero
    return Offset(location.x.toFloat(), location.y.toFloat())
}
