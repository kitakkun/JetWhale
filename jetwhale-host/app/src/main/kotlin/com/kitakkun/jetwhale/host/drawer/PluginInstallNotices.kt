package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import com.kitakkun.jetwhale.host.model.isActive
import com.kitakkun.jetwhale.host.notice_dismiss
import com.kitakkun.jetwhale.host.plugin_install_failed_message
import com.kitakkun.jetwhale.host.plugin_install_open
import com.kitakkun.jetwhale.host.plugin_install_retry
import com.kitakkun.jetwhale.host.plugin_installed_message
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwSnackbarResult
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.getString

/**
 * One notice per finished install, wherever the user is: an install outlives the settings page that
 * started it. A failure stays until the user acts on it; a success also leaves on its own.
 *
 * A notice and the install's entry in the plugin settings go together: [onDismiss] is called when a
 * notice leaves without a retry, and an install dismissed elsewhere takes its notice off the screen,
 * or out of the queue.
 */
@Composable
internal fun PluginInstallNotices(
    installJobs: ImmutableList<PluginInstallJob>,
    snackbarHostState: JwSnackbarHostState,
    onOpen: (PluginInstallJob) -> Unit,
    onRetry: (PluginInstallRequest) -> Unit,
    onDismiss: (jobId: String) -> Unit,
) {
    // A notice can wait in the snackbar queue for a while, so it acts through the callbacks of the
    // latest composition, not those of the one that queued it.
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnRetry by rememberUpdatedState(onRetry)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    installJobs.filterNot { it.status.isActive }.forEach { job ->
        key(job.id) {
            LaunchedEffect(Unit) {
                when (val status = job.status) {
                    PluginInstallStatus.Succeeded -> {
                        val result = snackbarHostState.showSnackbar(
                            message = getString(Res.string.plugin_installed_message, job.request.displayName),
                            actionLabel = getString(Res.string.plugin_install_open),
                            duration = JwSnackbarDuration.Long,
                            dismissLabel = getString(Res.string.notice_dismiss),
                        )
                        if (result == JwSnackbarResult.ActionPerformed) currentOnOpen(job)
                        currentOnDismiss(job.id)
                    }

                    is PluginInstallStatus.Failed -> {
                        val result = snackbarHostState.showSnackbar(
                            message = getString(Res.string.plugin_install_failed_message, job.request.displayName, status.reason),
                            actionLabel = getString(Res.string.plugin_install_retry),
                            duration = JwSnackbarDuration.Indefinite,
                            dismissLabel = getString(Res.string.notice_dismiss),
                        )
                        when (result) {
                            // The retry replaces this install in the list, so there is nothing to dismiss.
                            JwSnackbarResult.ActionPerformed -> currentOnRetry(job.request)

                            JwSnackbarResult.Dismissed -> currentOnDismiss(job.id)
                        }
                    }

                    PluginInstallStatus.Queued, is PluginInstallStatus.Running -> Unit
                }
            }
        }
    }
}
