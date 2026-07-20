package com.kitakkun.jetwhale.host.navigation

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.kitakkun.jetwhale.host.LocalComposeWindow
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.app_icon
import com.kitakkun.jetwhale.host.ui.isShortcutModifierPressed
import org.jetbrains.compose.resources.painterResource

data class WindowProperties(
    val windowPlacement: WindowPlacement = WindowPlacement.Floating,
    val width: Dp,
    val height: Dp,
)

internal data class WindowEntry<T : Any>(
    val entry: NavEntry<T>,
    val properties: WindowProperties,
)

// An OverlayScene that renders a single window. Each window is its own scene (like the built-in
// DialogScene), so its identity is only its own content key: it is independent of the entries drawn
// below it (overlaidEntries) and of every other window, and NavDisplay never tears it down on unrelated
// navigation. Because the scene stays alive, the per-window [rememberWindowState] survives too, so each
// window keeps its position and size.
internal class WindowOverlayScene<T : Any>(
    private val windowEntry: WindowEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onCloseRequest: (NavEntry<T>) -> Unit,
) : OverlayScene<T> {
    override val key: Any = windowEntry.entry.contentKey

    override val content: @Composable () -> Unit = {
        val windowState = rememberWindowState(
            placement = windowEntry.properties.windowPlacement,
            width = windowEntry.properties.width,
            height = windowEntry.properties.height,
        )

        Window(
            state = windowState,
            icon = painterResource(Res.drawable.app_icon),
            onCloseRequest = { onCloseRequest(windowEntry.entry) },
            onPreviewKeyEvent = { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isShortcutModifierPressed && keyEvent.key == Key.W) {
                    onCloseRequest(windowEntry.entry)
                    true
                } else {
                    false
                }
            },
        ) {
            CompositionLocalProvider(LocalComposeWindow provides this.window) {
                Surface {
                    windowEntry.entry.Content()
                }
            }
        }
    }

    override val entries: List<NavEntry<T>> = listOf(windowEntry.entry)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowOverlayScene<*>) return false

        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

class WindowSceneStrategy<T : Any>(
    private val onCloseRequestForContentKey: (Any) -> Unit,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        // Render one window per scene. The framework re-runs the scene strategies over overlaidEntries, so
        // returning the topmost window here and leaving the rest below lets each window become its own
        // independent overlay scene, stacked in order.
        val windowIndex = entries.indexOfLast { it.metadata[WINDOW_KEY] is WindowProperties }
        if (windowIndex == -1) return null

        val entry = entries[windowIndex]
        val properties = entry.metadata[WINDOW_KEY] as WindowProperties
        val overlaidEntries = entries.filterIndexed { index, _ -> index != windowIndex }

        val onCloseRequest: (NavEntry<T>) -> Unit = { closedEntry ->
            onCloseRequestForContentKey(closedEntry.contentKey)
        }

        return WindowOverlayScene(
            windowEntry = WindowEntry(entry, properties),
            previousEntries = overlaidEntries,
            overlaidEntries = overlaidEntries,
            onCloseRequest = onCloseRequest,
        )
    }

    companion object {
        fun window(
            windowProperties: WindowProperties,
        ): Map<String, Any> = mapOf(WINDOW_KEY to windowProperties)

        const val WINDOW_KEY = "window"
    }
}
