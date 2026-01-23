package com.kitakkun.jetwhale.host.navigation

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
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

data class WindowProperties(
    val windowPlacement: WindowPlacement = WindowPlacement.Floating,
    val width: Dp,
    val height: Dp,
)

internal data class WindowEntry<T : Any>(
    val entry: NavEntry<T>,
    val properties: WindowProperties,
)

internal class WindowOverlayScene<T : Any>(
    override val key: Any,
    private val windowEntries: List<WindowEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onCloseRequest: (NavEntry<T>) -> Unit,
) : OverlayScene<T> {
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
                    Surface {
                        windowEntry.entry.Content()
                    }
                }
            }
        }
    }

    override val entries: List<NavEntry<T>> = windowEntries.map { it.entry }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WindowOverlayScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            windowEntries == other.windowEntries
    }

    override fun hashCode(): Int {
        return key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            windowEntries.hashCode() * 31
    }

    override fun toString(): String {
        return "WindowOverlayScene(key=$key, entries=$windowEntries, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries)"
    }
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

        val key = windowEntries.last().entry.contentKey

        return WindowOverlayScene(
            key = key,
            windowEntries = windowEntries,
            previousEntries = nonWindowEntries,
            overlaidEntries = nonWindowEntries,
            onCloseRequest = onCloseRequest
        )
    }

    companion object {
        fun window(
            windowProperties: WindowProperties,
        ): Map<String, Any> = mapOf(WINDOW_KEY to windowProperties)

        const val WINDOW_KEY = "window"
    }
}
