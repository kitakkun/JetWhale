package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme

/** A notice for previews, which only draw. */
internal class PreviewNotices(override val notice: MirrorNotice?) : MirrorNoticeActions {
    override fun perform(action: NoticeAction) = Unit

    override fun dismiss() = Unit

    override fun hold(held: Boolean) = Unit
}

private val previewNotices = listOf(
    MirrorNotice("Saved 20260926-125424-screenshot.png", isError = false, actions = listOf(NoticeAction.OpenCaptures), details = emptyList()),
    MirrorNotice("Saved 2 of 3 screenshots", isError = true, actions = listOf(NoticeAction.OpenCaptures, NoticeAction.RetryScreenshots(listOf("00008110-DEVICE"))), details = listOf("iPhone 13: idb could not reach the device; unlock it and trust this Mac")),
    MirrorNotice.failure("Could not start recording Pixel 9: screenrecord is not available on this device", retry = NoticeAction.RetryRecording("emulator-5554")),
)

@Preview
@Composable
private fun MirrorNoticePreview() {
    listOf(true, false).forEach { dark ->
        JwTheme(darkTheme = dark) {
            Column(Modifier.padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                previewNotices.forEach { notice -> MirrorNoticeHost(PreviewNotices(notice)) }
            }
        }
    }
}
