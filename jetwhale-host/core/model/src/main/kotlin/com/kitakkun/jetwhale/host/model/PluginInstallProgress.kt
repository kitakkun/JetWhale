package com.kitakkun.jetwhale.host.model

/**
 * Progress of an in-flight plugin installation from a Maven repository. Installation downloads the
 * plugin jar, then each external dependency the plugin declares, then loads the plugin — each step
 * involves the network, so the UI surfaces which one is running.
 */
sealed interface PluginInstallProgress {
    data object DownloadingPlugin : PluginInstallProgress

    data class DownloadingDependencies(val completed: Int, val total: Int) : PluginInstallProgress

    data object LoadingPlugin : PluginInstallProgress
}
