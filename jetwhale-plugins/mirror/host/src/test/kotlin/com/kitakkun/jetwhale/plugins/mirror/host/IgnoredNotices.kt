package com.kitakkun.jetwhale.plugins.mirror.host

/** Notice actions for tests that show no notice. */
internal object IgnoredNotices : MirrorNoticeActions {
    override val notice: MirrorNotice? get() = null

    override fun dismissAndRun(action: NoticeAction) = Unit

    override fun dismiss() = Unit

    override fun hold(held: Boolean) = Unit
}
