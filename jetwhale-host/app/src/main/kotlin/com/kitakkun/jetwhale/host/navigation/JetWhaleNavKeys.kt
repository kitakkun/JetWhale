package com.kitakkun.jetwhale.host.navigation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import kotlinx.serialization.Serializable

/**
 * A key whose entry is drawn over the main window's content — a dialog or a window of its own —
 * rather than as that content. [presentation] is the one statement of how: the entry in NavEntries
 * takes its scene metadata from it, and [showBelowOverlays] leaves every such key where it is when
 * the content underneath changes.
 */
sealed interface OverlayNavKey : NavKey {
    val presentation: OverlayPresentation
}

sealed interface OverlayPresentation {
    data class Dialog(val properties: DialogProperties) : OverlayPresentation

    data class Window(val properties: WindowProperties) : OverlayPresentation

    val metadata: Map<String, Any>
        get() = when (this) {
            is Dialog -> StableDialogSceneStrategy.dialog(properties)
            is Window -> WindowSceneStrategy.window(properties)
        }
}

private val FULL_WIDTH_DIALOG = OverlayPresentation.Dialog(
    DialogProperties(usePlatformDefaultWidth = false),
)

@Serializable
data object EmptyPluginNavKey : NavKey

@Serializable
data class SettingsNavKey(
    val initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
) : OverlayNavKey {
    override val presentation: OverlayPresentation get() = PRESENTATION

    companion object {
        val PRESENTATION: OverlayPresentation = FULL_WIDTH_DIALOG
    }
}

@Serializable
data object LicensesNavKey : OverlayNavKey {
    override val presentation: OverlayPresentation get() = FULL_WIDTH_DIALOG
}

@Serializable
data object InfoNavKey : OverlayNavKey {
    override val presentation: OverlayPresentation get() = OverlayPresentation.Dialog(DialogProperties())
}

@Serializable
data class PluginNavKey(
    val pluginId: String,
    val sessionId: String,
) : NavKey

@Serializable
data class PluginPopoutNavKey(
    val pluginId: String,
    val sessionId: String,
    val pluginName: String,
) : OverlayNavKey {
    override val presentation: OverlayPresentation get() = PRESENTATION

    companion object {
        val PRESENTATION: OverlayPresentation = OverlayPresentation.Window(
            WindowProperties(width = 800.dp, height = 600.dp),
        )
    }
}

@Serializable
data object DisabledPluginNavKey : NavKey

@Serializable
data object LogViewerNavKey : OverlayNavKey {
    override val presentation: OverlayPresentation get() = OverlayPresentation.Window(
        WindowProperties(width = 1000.dp, height = 700.dp),
    )
}

/**
 * The MCP tools browser. [pluginId] and [sessionId] seed the screen's filters — null means
 * "all", so opening it from a plugin's badge lands on that plugin while the screen itself can
 * widen the view afterwards.
 */
@Serializable
data class McpToolsNavKey(
    val pluginId: String?,
    val sessionId: String?,
) : OverlayNavKey {
    override val presentation: OverlayPresentation get() = PRESENTATION

    companion object {
        // The browser sizes itself; the platform default width would squeeze it to a narrow column.
        val PRESENTATION: OverlayPresentation = FULL_WIDTH_DIALOG
    }
}
