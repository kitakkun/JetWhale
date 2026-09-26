package com.kitakkun.jetwhale.host.model

/**
 * A plugin the user or an agent asked the host to download and install.
 *
 * @property key identifies requests for the same plugin, so asking again while one is queued or
 *   running joins it instead of starting a second download.
 * @property pluginId the plugin id the install is known to provide, when the request names it.
 */
sealed interface PluginInstallRequest {
    val key: String
    val displayName: String
    val pluginId: String?

    data class Official(val plugin: OfficialPlugin) : PluginInstallRequest {
        override val key: String get() = "official:${plugin.pluginId}"
        override val displayName: String get() = plugin.displayName
        override val pluginId: String get() = plugin.pluginId
    }

    data class Maven(val coordinates: MavenCoordinates) : PluginInstallRequest {
        override val key: String get() = "maven:${coordinates.groupId}:${coordinates.artifactId}"
        override val displayName: String get() = coordinates.artifactId
        override val pluginId: String? get() = null
    }
}

/** One install the host is running or has finished, from queued to its outcome. */
data class PluginInstallJob(
    val id: String,
    val request: PluginInstallRequest,
    val status: PluginInstallStatus,
)

/**
 * Where an install is. A cancelled install leaves no job behind: whoever cancelled it already knows.
 */
sealed interface PluginInstallStatus {
    /** Waiting for the install ahead of it; installs run one at a time. */
    data object Queued : PluginInstallStatus

    /** [progress] is null between steps. */
    data class Running(val progress: PluginInstallProgress?) : PluginInstallStatus

    data object Succeeded : PluginInstallStatus

    data class Failed(val reason: String) : PluginInstallStatus
}

/** Whether the install has yet to reach an outcome. */
val PluginInstallStatus.isActive: Boolean
    get() = this is PluginInstallStatus.Queued || this is PluginInstallStatus.Running

/**
 * Whether cancelling still undoes the install. Once the plugin is being loaded, its jar is already in
 * the plugins directory and is approved; that step finishes rather than stop halfway.
 */
val PluginInstallStatus.isCancellable: Boolean
    get() = this is PluginInstallStatus.Queued ||
        (this is PluginInstallStatus.Running && progress != PluginInstallProgress.LoadingPlugin)
