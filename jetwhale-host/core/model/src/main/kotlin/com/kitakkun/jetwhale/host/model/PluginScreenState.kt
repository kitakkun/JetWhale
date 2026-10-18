package com.kitakkun.jetwhale.host.model

import androidx.compose.ui.InternalComposeUiApi

/** What a plugin's screen in one session can show, as its instance starts, runs, fails or goes away. */
sealed interface PluginScreenState {
    /** No instance: it is not created yet, or the plugin is not running in that session. */
    data object Starting : PluginScreenState

    @OptIn(InternalComposeUiApi::class)
    data class Ready(val scene: PluginComposeScene) : PluginScreenState

    /** The instance runs but renders no UI. */
    data object Headless : PluginScreenState

    /**
     * The last attempt to create the instance threw [cause]. It stays so until an attempt succeeds,
     * which takes a new one from outside the screen: re-enabling the plugin, or, in an app's session,
     * the app reconnecting.
     */
    data class FailedToStart(val cause: Throwable) : PluginScreenState

    /**
     * The instance runs, but its content threw [cause] while its scene was first composed. It turns
     * [Ready] as soon as a later attempt composes the scene, from a retry or an MCP tool.
     */
    data class ContentFailed(val cause: Throwable) : PluginScreenState
}
