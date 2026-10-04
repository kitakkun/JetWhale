package com.kitakkun.jetwhale.plugins.permissions.agent

/**
 * What a request for a denied Android runtime permission would do, as far as the agent has seen.
 * Android's shouldShowRequestPermissionRationale answers true only between a first denial and a
 * permanent one, so a permanent denial shows only as that answer turning false; a permission never
 * seen with it true cannot be told from one never asked. Kept free of Android types so the
 * transitions can be tested everywhere.
 */
internal enum class RuntimeDenial(val note: String) {
    Unknown("Never asked, or denied permanently; Android does not tell these apart until the app asks."),
    Once("Denied once; a request shows the dialog again."),
    Permanently("Denied permanently: a request no longer shows a dialog. Change it in the app's settings."),
    ;

    /**
     * The denial after a read whose rationale answer was [rationale], or null when no activity was
     * in the foreground to ask.
     */
    fun after(rationale: Boolean?): RuntimeDenial = when (rationale) {
        null -> this
        true -> Once
        false -> if (this == Unknown) Unknown else Permanently
    }
}
