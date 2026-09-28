package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Long enough to read a file name and reach Open, short enough that the notice is gone before the next one. */
internal const val SUCCESS_NOTICE_MILLIS = 6_000L

/** What a button on a notice does. */
internal sealed interface NoticeAction {
    val label: String

    /** Shows [capture] in the captures panel. */
    data class OpenCapture(val capture: Capture) : NoticeAction {
        override val label: String get() = "Open"
    }

    /** Shows the captures panel with every device's captures. */
    data object OpenCaptures : NoticeAction {
        override val label: String get() = "Open Captures"
    }

    /** Takes a screenshot of each of [deviceIds] again. */
    data class RetryScreenshots(val deviceIds: List<String>) : NoticeAction {
        override val label: String get() = "Retry"
    }

    /** Starts recording [deviceId] again. */
    data class RetryRecording(val deviceId: String) : NoticeAction {
        override val label: String get() = "Retry"
    }
}

/**
 * A message about something the mirror did. A failure stays until it is dismissed; anything else
 * leaves on its own after [SUCCESS_NOTICE_MILLIS].
 *
 * @property details the individual reasons behind a partial failure, shown on request.
 */
internal class MirrorNotice(
    val message: String,
    val isError: Boolean,
    val actions: List<NoticeAction>,
    val details: List<String>,
) {
    companion object {
        fun info(message: String): MirrorNotice = MirrorNotice(message, isError = false, actions = emptyList(), details = emptyList())

        fun failure(message: String, retry: NoticeAction?): MirrorNotice = MirrorNotice(message, isError = true, actions = listOfNotNull(retry), details = emptyList())

        fun saved(capture: Capture): MirrorNotice = MirrorNotice("Saved ${capture.file.name}", isError = false, actions = listOf(NoticeAction.OpenCapture(capture)), details = emptyList())

        /** How saving a screenshot of each device went; Retry takes the failed ones again. */
        fun screenshotsSaved(results: List<ScreenshotResult>): MirrorNotice {
            val saved = results.filterIsInstance<ScreenshotResult.Saved>()
            val failed = results.filterIsInstance<ScreenshotResult.Failed>()
            val retry = NoticeAction.RetryScreenshots(failed.map(ScreenshotResult.Failed::deviceId))
            val reasons = failed.map { "${it.deviceName}: ${it.reason}" }
            return when {
                failed.isEmpty() && saved.size == 1 -> saved(saved.single().capture)
                failed.isEmpty() -> MirrorNotice("Saved ${saved.size} screenshots", isError = false, actions = listOf(NoticeAction.OpenCaptures), details = emptyList())
                results.size == 1 -> failure("Could not save a screenshot of ${failed.single().deviceName}: ${failed.single().reason}", retry)
                saved.isEmpty() -> MirrorNotice("Could not save the screenshots", isError = true, actions = listOf(retry), details = reasons)
                else -> MirrorNotice("Saved ${saved.size} of ${results.size} screenshots", isError = true, actions = listOf(NoticeAction.OpenCaptures, retry), details = reasons)
            }
        }
    }
}

/** How saving one device's screenshot went. */
internal sealed interface ScreenshotResult {
    data class Saved(val capture: Capture) : ScreenshotResult

    data class Failed(val deviceId: String, val deviceName: String, val reason: String) : ScreenshotResult
}

/** The notice on screen, and what its UI can ask of it. */
internal interface MirrorNoticeActions {
    val notice: MirrorNotice?

    /** Dismisses the notice, then does what [action] says. */
    fun perform(action: NoticeAction)

    fun dismiss()

    /** Keeps the notice up while the pointer is over it or it has focus, so it is not taken away mid-read. */
    fun hold(held: Boolean)
}

/** The one notice the mirror shows at a time. A newer notice replaces the one on screen. */
@Stable
internal class MirrorNotices(private val scope: CoroutineScope) {
    var current: MirrorNotice? by mutableStateOf(null)
        private set

    private var held = false
    private var expiry: Job? = null

    fun show(notice: MirrorNotice) {
        current = notice
        scheduleExpiry()
    }

    fun dismiss() {
        expiry?.cancel()
        current = null
    }

    fun hold(held: Boolean) {
        this.held = held
        scheduleExpiry()
    }

    // Restarts the full wait on release rather than resuming it, so a notice never vanishes the
    // moment the pointer leaves it.
    private fun scheduleExpiry() {
        expiry?.cancel()
        val notice = current ?: return
        if (notice.isError || held) return
        expiry = scope.launch {
            delay(SUCCESS_NOTICE_MILLIS)
            if (current === notice) current = null
        }
    }
}
