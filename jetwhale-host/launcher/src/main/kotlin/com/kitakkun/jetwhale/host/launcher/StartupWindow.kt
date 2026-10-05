package com.kitakkun.jetwhale.host.launcher

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The time after a host's start in which a crash counts against the start. It is the startup grace of
 * the host's crash recovery, so the launcher and the host count the same crashes as startup crashes.
 */
class StartupWindow(
    private val length: Duration,
    private val pollInterval: Duration,
    private val timeSource: TimeSource,
    private val sleep: (Duration) -> Unit,
) {
    /**
     * Watches [startup] from now, which is right before the host's main is called, to the end of the
     * window: tells it once [isPublished] says the host has published its record, and then that the
     * window has ended.
     */
    fun watch(startup: HostLauncher.HostStartup, isPublished: () -> Boolean) {
        val started = timeSource.markNow()
        var published = false
        while (started.elapsedNow() < length) {
            if (!published && isPublished()) {
                published = true
                startup.hostPublished()
            }
            sleep(pollInterval)
        }
        startup.windowEnded()
    }
}
