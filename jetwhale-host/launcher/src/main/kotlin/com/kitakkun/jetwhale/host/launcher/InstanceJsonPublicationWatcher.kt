package com.kitakkun.jetwhale.host.launcher

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Watches for the started host to publish `instance.json`, so that its [HostLauncher.HostStartup] can
 * let `launch.lock` go. It gives up at the end of the startup time window, whose recorded end lets the
 * lock go in any case.
 */
class InstanceJsonPublicationWatcher(
    private val startupTimeWindow: Duration,
    private val pollInterval: Duration,
    private val timeSource: TimeSource,
    private val sleep: (Duration) -> Unit,
    private val isInstanceJsonPublished: () -> Boolean,
) {
    /**
     * Polls from now, which is right before the host's main is called, until [isInstanceJsonPublished]
     * says the host has published `instance.json`, and then tells [startup] once.
     */
    fun reportPublicationTo(startup: HostLauncher.HostStartup) {
        val started = timeSource.markNow()
        while (started.elapsedNow() < startupTimeWindow) {
            if (isInstanceJsonPublished()) {
                startup.hostPublishedInstanceJson()
                return
            }
            sleep(pollInterval)
        }
    }
}
