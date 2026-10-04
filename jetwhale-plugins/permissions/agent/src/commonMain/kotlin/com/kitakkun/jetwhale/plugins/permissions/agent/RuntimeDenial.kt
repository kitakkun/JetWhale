package com.kitakkun.jetwhale.plugins.permissions.agent

/**
 * What a request for a denied Android runtime permission would do, as far as the agent has seen.
 * Android's shouldShowRequestPermissionRationale answers true once the user has denied a
 * permission that a request can still ask about, and false both before the first request and after
 * a permanent denial. A permanent denial therefore shows only as that answer turning false, and a
 * permission never seen with it true cannot be told from one never asked. Background location
 * breaks this rule in the cases [backgroundLocationOf] covers. Kept free of Android types so the
 * transitions can be tested everywhere.
 */
internal enum class RuntimeDenial(override val note: String) : RequestOutlook {
    Unknown("Never asked, or denied permanently; Android does not tell these apart until the app asks."),
    Once("Denied once; a request shows the dialog again."),
    Permanently("Denied permanently: a request no longer shows a dialog. Change it in the app's settings."),
    ;

    override val requestable: Boolean get() = this != Permanently

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
