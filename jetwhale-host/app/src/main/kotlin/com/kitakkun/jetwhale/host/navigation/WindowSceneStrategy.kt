package com.kitakkun.jetwhale.host.navigation

import androidx.compose.runtime.Composable
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

internal class WindowScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
    private val windowProperties: WindowProperties,
) : OverlayScene<T> {
    override val content: @Composable () -> Unit = {
        Window(
            state = rememberWindowState(
                placement = windowProperties.windowPlacement,
                width = windowProperties.width,
                height = windowProperties.height,
            ),
            onCloseRequest = onBack,
        ) {
            entry.Content()
        }
    }

    override val entries: List<NavEntry<T>> = listOf(entry)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WindowScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry &&
            windowProperties == other.windowProperties
    }

    override fun hashCode(): Int {
        return key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            entry.hashCode() * 31 +
            windowProperties.hashCode() * 31
    }

    override fun toString(): String {
        return "WindowOverlayScene(key=$key, entry=$entry, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries, windowProperties=$windowProperties)"
    }
}

class WindowSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val windowProperties = lastEntry.metadata.get(WINDOW_KEY) as? WindowProperties ?: return null
        val overlaidEntries = entries.dropLast(1)
        return WindowScene(
            key = lastEntry.contentKey,
            entry = lastEntry,
            previousEntries = overlaidEntries,
            overlaidEntries = overlaidEntries,
            onBack = onBack,
            windowProperties = windowProperties,
        )
    }

    companion object {
        fun window(
            windowProperties: WindowProperties,
        ): Map<String, Any> = mapOf(WINDOW_KEY to windowProperties)

        const val WINDOW_KEY = "window"
    }
}
