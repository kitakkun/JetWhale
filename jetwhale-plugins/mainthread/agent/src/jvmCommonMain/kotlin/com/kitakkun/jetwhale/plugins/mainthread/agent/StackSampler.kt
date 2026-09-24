package com.kitakkun.jetwhale.plugins.mainthread.agent

/** Deeper frames are the thread's entry and the event loop, the same in every sample. */
private const val MAX_SAMPLED_FRAMES = 64

/** The sampler never ticks more often than this, whatever the sample interval is set to. */
private const val MIN_TICK_MILLIS = 5L

/**
 * A daemon thread that ticks at half the sample interval and asks [recorder] whether the main
 * thread's current task has run long enough to be sampled. While no task is long, a tick costs a
 * lock and a comparison; the stack of [mainThread] is only read when a sample is due.
 */
internal class StackSampler(
    private val recorder: MainThreadRecorder,
    private val mainThread: () -> Thread?,
) {
    @Volatile private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread(::run, "jetwhale-main-thread-sampler").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        thread?.interrupt()
        thread = null
    }

    private fun run() {
        val self = Thread.currentThread()
        while (thread === self) {
            val target = mainThread()
            if (target != null) {
                recorder.sampleIfDue { target.stackTrace.take(MAX_SAMPLED_FRAMES).map(::frameText) }
            }
            try {
                Thread.sleep((recorder.currentSettings.sampleIntervalMillis / 2).coerceAtLeast(MIN_TICK_MILLIS))
            } catch (_: InterruptedException) {
                return
            }
        }
    }
}
