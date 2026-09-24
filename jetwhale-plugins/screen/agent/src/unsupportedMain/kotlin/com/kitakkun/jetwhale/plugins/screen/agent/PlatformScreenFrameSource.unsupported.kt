package com.kitakkun.jetwhale.plugins.screen.agent

import com.kitakkun.jetwhale.plugins.screen.protocol.ScreenFrame
import com.kitakkun.jetwhale.plugins.screen.protocol.StartScreenStream

internal actual fun platformScreenFrameSource(): ScreenFrameSource = UnsupportedScreenFrameSource

private object UnsupportedScreenFrameSource : ScreenFrameSource {
    override val unsupportedReason: String get() = "screen streaming is only implemented on Android so far"

    override fun start(settings: StartScreenStream, onFrame: (ScreenFrame) -> Unit) = Unit

    override fun grant(count: Int) = Unit

    override fun stop() = Unit
}
