package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.InvocationEvent

internal actual fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?): MainThreadProbe = if (GraphicsEnvironment.isHeadless()) {
    UnsupportedMainThreadProbe(platform = "JVM", reason = "This JVM is headless: it has no AWT event dispatch thread to watch.")
} else {
    EventDispatchThreadProbe(recorder, labels)
}

/**
 * Times every event the AWT event dispatch thread handles — Compose Desktop's main dispatcher runs
 * its work there too — by putting a timing queue on top of the system event queue, and samples the
 * dispatch thread while an event runs long. StrictMode and frame timing have no JVM counterpart.
 */
private class EventDispatchThreadProbe(
    private val recorder: MainThreadRecorder,
    private val labels: () -> String?,
) : MainThreadProbe {
    override val capabilities = MonitorCapabilities(
        platform = "JVM (AWT/Swing)",
        taskTiming = true,
        stackSampling = true,
        strictMode = false,
        frameTiming = false,
        note = "StrictMode and frame timing exist only on Android.",
    )

    @Volatile private var dispatchThread: Thread? = null
    private var queue: TimingEventQueue? = null

    // Every event the dispatch thread handles passes through the timing queue, so there is no work
    // outside a task for a heartbeat to find.
    private val sampler = StackSampler(recorder, mainThread = { dispatchThread }, onTick = {})

    override fun start() {
        if (queue != null) return
        queue = TimingEventQueue().also(Toolkit.getDefaultToolkit().systemEventQueue::push)
        sampler.start()
    }

    override fun stop() {
        sampler.stop()
        queue?.detach()
        queue = null
    }

    private inner class TimingEventQueue : EventQueue() {
        // A modal dialog pumps events from inside another event's dispatch; only the outermost
        // event is a task of its own.
        private var depth = 0

        override fun dispatchEvent(event: AWTEvent) {
            if (depth++ == 0) {
                dispatchThread = Thread.currentThread()
                recorder.taskStarted(describe(event), labels())
            }
            try {
                super.dispatchEvent(event)
            } finally {
                if (--depth == 0) recorder.taskFinished()
            }
        }

        /** Hands dispatch back to the queue underneath and stops naming a thread for the sampler. */
        fun detach() {
            dispatchThread = null
            pop()
        }
    }
}

/** An event by its kind and, for a posted Runnable, the Runnable's class: what posted the work. */
private fun describe(event: AWTEvent): String = when (event) {
    is InvocationEvent -> "InvocationEvent " + event.paramString().substringAfter("runnable=", "").substringBefore(',').substringBefore('@').let(::withoutHiddenClassAddress)
    else -> "${event.javaClass.simpleName} on ${event.source?.javaClass?.simpleName}"
}
