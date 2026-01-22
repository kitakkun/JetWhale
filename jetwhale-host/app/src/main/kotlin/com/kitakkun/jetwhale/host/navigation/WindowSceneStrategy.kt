package com.kitakkun.jetwhale.host.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation3.runtime.NavEntry
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
    override val previousEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit,
    private val windowProperties: WindowProperties,
) : Scene<T> {
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
}

class WindowSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val windowProperties = lastEntry.metadata.get(WINDOW_KEY) as? WindowProperties ?: return null
        return WindowScene(
            key = lastEntry.contentKey,
            previousEntries = entries.dropLast(1),
            entry = lastEntry,
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
