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

    /** The last attempt to create the instance threw [cause]; it stays so until an attempt succeeds. */
    data class Failed(val cause: Throwable) : PluginScreenState
}
