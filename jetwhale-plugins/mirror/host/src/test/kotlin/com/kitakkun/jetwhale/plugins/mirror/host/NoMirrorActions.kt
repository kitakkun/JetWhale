package com.kitakkun.jetwhale.plugins.mirror.host

internal object NoMirrorActions : MirrorActions {
    override fun select(deviceId: String) = Unit

    override fun tap(x: Int, y: Int) = Unit

    override fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int) = Unit

    override fun pressButton(button: DeviceButton) = Unit

    override fun inputText(text: String) = Unit

    override fun saveScreenshot() = Unit

    override fun recordSelectedDevice() = Unit

    override fun stopSelectedRecording() = Unit

    override fun wake() = Unit

    override fun sleep() = Unit

    override fun retryLiveVideo() = Unit

    override val developmentTeam: String? get() = null

    override fun updateDevelopmentTeam(team: String?) = Unit
}
