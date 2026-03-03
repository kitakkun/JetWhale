package com.kitakkun.jetwhale.host.navigation

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
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

data class WindowProperties(
    val windowPlacement: WindowPlacement = WindowPlacement.Floating,
    val width: Dp,
    val height: Dp,
)

internal data class WindowEntry<T : Any>(
    val entry: NavEntry<T>,
    val properties: WindowProperties,
)

internal data class WindowOverlayScene<T : Any>(
    private val windowEntries: List<WindowEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onCloseRequest: (NavEntry<T>) -> Unit,
) : OverlayScene<T> {
    override val key: Any = windowEntries.map { it.entry.contentKey }.joinToString(separator = "_")

    override val content: @Composable () -> Unit = {
        windowEntries.forEach { windowEntry ->
            key(windowEntry.entry.contentKey) {
                val windowState = rememberWindowState(
                    placement = windowEntry.properties.windowPlacement,
                    width = windowEntry.properties.width,
                    height = windowEntry.properties.height,
                )

                Window(
                    state = windowState,
                    onCloseRequest = { onCloseRequest(windowEntry.entry) },
                ) {
                    CompositionLocalProvider(LocalComposeWindow provides this.window) {
                        Surface {
                            windowEntry.entry.Content()
                        }
                    }
                }
            }
        }
    }

    override val entries: List<NavEntry<T>> = windowEntries.map { it.entry }
}

class WindowSceneStrategy<T : Any>(
    private val onCloseRequestForContentKey: (Any) -> Unit,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.isEmpty()) return null

        val windowEntries = entries.mapNotNull { entry ->
            val windowProperties = entry.metadata[WINDOW_KEY] as? WindowProperties ?: return@mapNotNull null
            WindowEntry(entry, windowProperties)
        }

        if (windowEntries.isEmpty()) return null

        val nonWindowEntries = entries.filterNot { entry ->
            entry.metadata[WINDOW_KEY] is WindowProperties
        }

        val onCloseRequest: (NavEntry<T>) -> Unit = { entry ->
            onCloseRequestForContentKey(entry.contentKey)
        }

        return WindowOverlayScene(
            windowEntries = windowEntries,
            previousEntries = nonWindowEntries,
            overlaidEntries = nonWindowEntries,
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
