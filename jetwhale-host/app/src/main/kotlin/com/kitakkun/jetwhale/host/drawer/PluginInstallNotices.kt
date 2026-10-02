package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import com.kitakkun.jetwhale.host.model.isActive
import com.kitakkun.jetwhale.host.notice_dismiss
import com.kitakkun.jetwhale.host.plugin_install_failed_message
import com.kitakkun.jetwhale.host.plugin_install_failed_several_message
import com.kitakkun.jetwhale.host.plugin_install_mixed_message
import com.kitakkun.jetwhale.host.plugin_install_more
import com.kitakkun.jetwhale.host.plugin_install_open
import com.kitakkun.jetwhale.host.plugin_install_retry
import com.kitakkun.jetwhale.host.plugin_install_review
import com.kitakkun.jetwhale.host.plugin_install_view
import com.kitakkun.jetwhale.host.plugin_installed_message
import com.kitakkun.jetwhale.host.plugin_installed_several_message
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwSnackbarResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.getString

/**
 * One notice for all the installs that finished, wherever the user is: an install outlives the
 * settings page that started it. Installs finishing close together, or while the notice is up, join
 * it instead of queueing a notice each; the notice is then shown again with all of them.
 *
 * A single install keeps its own actions: open the plugin, or retry. Several are summed up with an
 * action to the page that lists them: the installed plugins, or, when any failed, the install list,
 * where each failure shows its reason and a retry. Successes also leave on their own; a failure stays
 * until the user acts on it.
 *
 * A notice and its installs' entries in the plugin settings go together: [onDismiss] is called once,
 * with every install a notice leaves with except the failures the user went to review, and an install
 * dismissed elsewhere leaves the notice.
 */
@Composable
internal fun PluginInstallNotices(
    installJobs: ImmutableList<PluginInstallJob>,
    snackbarHostState: JwSnackbarHostState,
    onOpen: (PluginInstallJob) -> Unit,
    onRetry: (PluginInstallRequest) -> Unit,
    onDismiss: (jobIds: List<String>) -> Unit,
    onShowInstalledPlugins: () -> Unit,
    onReviewInstalls: () -> Unit,
) {
    val currentInstallJobs by rememberUpdatedState(installJobs)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnRetry by rememberUpdatedState(onRetry)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnShowInstalledPlugins by rememberUpdatedState(onShowInstalledPlugins)
    val currentOnReviewInstalls by rememberUpdatedState(onReviewInstalls)

    val settledJobIds = remember { mutableStateSetOf<String>() }
    LaunchedEffect(installJobs) {
        val listedIds = installJobs.map(PluginInstallJob::id).toSet()
        settledJobIds.retainAll(listedIds)
    }

    LaunchedEffect(snackbarHostState) {
        snapshotFlow { currentInstallJobs.filter { !it.status.isActive && it.id !in settledJobIds } }
            .collectLatest { finished ->
                if (finished.isEmpty()) return@collectLatest
                delay(BATCH_WINDOW_MILLIS)
                val notice = noticeFor(finished)
                val result = snackbarHostState.showSnackbar(
                    message = notice.message,
                    actionLabel = notice.actionLabel,
                    duration = notice.duration,
                    dismissLabel = getString(Res.string.notice_dismiss),
                )
                // Settled only after the notice closes: a job finishing meanwhile restarts this
                // block through collectLatest, and the jobs already on the notice must be in the
                // next one too.
                settledJobIds += finished.map(PluginInstallJob::id)
                when (notice.action) {
                    is NoticeAction.Open -> {
                        if (result == JwSnackbarResult.ActionPerformed) currentOnOpen(notice.action.job)
                        currentOnDismiss(listOf(notice.action.job.id))
                    }

                    is NoticeAction.Retry -> when (result) {
                        JwSnackbarResult.ActionPerformed -> currentOnRetry(notice.action.job.request)
                        JwSnackbarResult.Dismissed -> currentOnDismiss(listOf(notice.action.job.id))
                    }

                    is NoticeAction.ShowInstalledPlugins -> {
                        if (result == JwSnackbarResult.ActionPerformed) currentOnShowInstalledPlugins()
                        currentOnDismiss(finished.map(PluginInstallJob::id))
                    }

                    is NoticeAction.ReviewInstalls -> when (result) {
                        JwSnackbarResult.ActionPerformed -> {
                            currentOnDismiss(finished.filter { it.status == PluginInstallStatus.Succeeded }.map(PluginInstallJob::id))
                            currentOnReviewInstalls()
                        }

                        JwSnackbarResult.Dismissed -> currentOnDismiss(finished.map(PluginInstallJob::id))
                    }
                }
            }
    }
}

private class InstallNotice(
    val message: String,
    val actionLabel: String,
    val duration: JwSnackbarDuration,
    val action: NoticeAction,
)

private sealed interface NoticeAction {
    class Open(val job: PluginInstallJob) : NoticeAction
    class Retry(val job: PluginInstallJob) : NoticeAction
    data object ShowInstalledPlugins : NoticeAction
    data object ReviewInstalls : NoticeAction
}

private suspend fun noticeFor(finished: List<PluginInstallJob>): InstallNotice {
    val successes = finished.filter { it.status == PluginInstallStatus.Succeeded }
    val failures = finished.filter { it.status is PluginInstallStatus.Failed }
    val single = finished.singleOrNull()
    val singleFailure = single?.status as? PluginInstallStatus.Failed
    return when {
        single != null && single.status == PluginInstallStatus.Succeeded -> InstallNotice(
            message = getString(Res.string.plugin_installed_message, single.request.displayName),
            actionLabel = getString(Res.string.plugin_install_open),
            duration = JwSnackbarDuration.Long,
            action = NoticeAction.Open(single),
        )

        single != null && singleFailure != null -> InstallNotice(
            message = getString(Res.string.plugin_install_failed_message, single.request.displayName, singleFailure.reason),
            actionLabel = getString(Res.string.plugin_install_retry),
            duration = JwSnackbarDuration.Indefinite,
            action = NoticeAction.Retry(single),
        )

        failures.isEmpty() -> InstallNotice(
            message = getString(Res.string.plugin_installed_several_message, namesOf(successes)),
            actionLabel = getString(Res.string.plugin_install_view),
            duration = JwSnackbarDuration.Long,
            action = NoticeAction.ShowInstalledPlugins,
        )

        else -> InstallNotice(
            message = if (successes.isEmpty()) {
                getString(Res.string.plugin_install_failed_several_message, namesOf(failures))
            } else {
                getString(Res.string.plugin_install_mixed_message, namesOf(successes), namesOf(failures))
            },
            actionLabel = getString(Res.string.plugin_install_review),
            duration = JwSnackbarDuration.Indefinite,
            action = NoticeAction.ReviewInstalls,
        )
    }
}

/** The first few plugin names, then how many more, so a long batch still fits the snackbar. */
private suspend fun namesOf(jobs: List<PluginInstallJob>): String {
    val names = jobs.take(MAX_NAMED_PLUGINS).joinToString(", ") { it.request.displayName }
    val unnamed = jobs.size - MAX_NAMED_PLUGINS
    return if (unnamed > 0) "$names, ${getString(Res.string.plugin_install_more, unnamed)}" else names
}

/** How long finishing installs are gathered into one notice before it shows. */
private const val BATCH_WINDOW_MILLIS = 1_000L

private const val MAX_NAMED_PLUGINS = 3
