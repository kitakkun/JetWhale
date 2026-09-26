package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin

/** Where a plugin's instance in one session stands. */
sealed interface PluginInstanceState {
    /** No instance: the plugin is disabled, not installed for that session, or not created yet. */
    data object Absent : PluginInstanceState

    data class Running(val plugin: JetWhaleHostPlugin) : PluginInstanceState

    /** The last attempt to create the instance threw [cause]; it stays so until an attempt succeeds. */
    data class FailedToStart(val cause: Throwable) : PluginInstanceState
}
