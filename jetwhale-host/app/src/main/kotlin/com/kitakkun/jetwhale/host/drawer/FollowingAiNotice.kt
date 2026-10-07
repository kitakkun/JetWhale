package com.kitakkun.jetwhale.host.drawer

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.snapshotFlow
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.following_ai_toast
import com.kitakkun.jetwhale.host.ui.JwSnackbarDuration
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * The notice that explains a move the main window made on its own to follow an AI agent.
 *
 * An agent can move the window several times in a few seconds, so a newer notice replaces the one
 * on screen or waiting for it instead of queueing behind it, and each stays only
 * [FOLLOWING_AI_NOTICE_DURATION_MILLIS] on screen. Other messages on [snackbarHostState] are left
 * alone: one already waiting shows first, and the notice waits behind it.
 */
internal class FollowingAiNotice(
    private val snackbarHostState: JwSnackbarHostState,
    private val coroutineScope: CoroutineScope,
) {
    private var latestNoticeJob: Job? = null

    fun show(pluginName: String) {
        latestNoticeJob?.cancel()
        latestNoticeJob = coroutineScope.launch {
            val message = getString(Res.string.following_ai_toast, pluginName)
            // JwSnackbarDuration has nothing shorter than Short, so the notice ends by cancelling
            // showSnackbar. The delay starts once it appears: a timeout around showSnackbar would
            // also count the wait behind an earlier message, and cut the notice short or drop it.
            val snackbarJob = launch { snackbarHostState.showSnackbar(message = message, duration = JwSnackbarDuration.Short) }
            snapshotFlow { snackbarHostState.currentSnackbarData?.message }.first { it == message }
            delay(FOLLOWING_AI_NOTICE_DURATION_MILLIS)
            snackbarJob.cancel()
        }
    }
}

/** How long the notice stays on screen, in milliseconds: long enough to read one short line. */
@VisibleForTesting
internal const val FOLLOWING_AI_NOTICE_DURATION_MILLIS = 2_000L
