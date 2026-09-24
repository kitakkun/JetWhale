package com.kitakkun.jetwhale.plugins.screen.agent

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.kitakkun.jetwhale.plugins.screen.protocol.ScreenFrame
import com.kitakkun.jetwhale.plugins.screen.protocol.StartScreenStream

internal actual fun platformScreenFrameSource(): ScreenFrameSource = AndroidScreenFrameSource()

/**
 * Owns the worker thread and the one [WindowStreamSession] that is running. Every call hops to the
 * main thread, where the session lives, so the session itself needs no locking.
 */
private class AndroidScreenFrameSource : ScreenFrameSource {
    // WindowInspector, which finds every window of the process (dialogs included), arrived in API 29.
    override val unsupportedReason: String?
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "screen streaming needs Android 10 (API 29) or later" else null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: HandlerThread? = null
    private var session: WindowStreamSession? = null

    override fun start(settings: StartScreenStream, onFrame: (ScreenFrame) -> Unit) {
        mainHandler.post {
            session?.close()
            val thread = worker ?: HandlerThread("jetwhale-screen-stream").also {
                it.start()
                worker = it
            }
            session = WindowStreamSession(settings, mainHandler, Handler(thread.looper), onFrame).also(WindowStreamSession::open)
        }
    }

    override fun grant(count: Int) {
        mainHandler.post { session?.grant(count) }
    }

    override fun stop() {
        mainHandler.post {
            session?.close()
            session = null
            worker?.quitSafely()
            worker = null
        }
    }
}
