package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * In-memory holder for the progress of the currently running plugin installation.
 * `null` means no installation is in flight.
 */
interface PluginInstallProgressRepository {
    val progressFlow: Flow<PluginInstallProgress?>

    fun update(progress: PluginInstallProgress?)
}
