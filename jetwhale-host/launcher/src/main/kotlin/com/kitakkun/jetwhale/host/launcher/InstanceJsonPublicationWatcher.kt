package com.kitakkun.jetwhale.host.launcher

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Watches for the started host to publish `instance.json`, where a later launch finds it, so that
 * `launch.lock` can go. It gives up at the end of the startup time window, whose recorded end lets the
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
     * says the host has published `instance.json`, and then has [startOutcomeRecorder] release
     * `launch.lock`.
     */
    fun releaseLaunchLockWhenInstanceJsonIsPublished(startOutcomeRecorder: HostLauncher.HostStartOutcomeRecorder) {
        val started = timeSource.markNow()
        while (started.elapsedNow() < startupTimeWindow) {
            if (isInstanceJsonPublished()) {
                startOutcomeRecorder.releaseLaunchLock()
                return
            }
            sleep(pollInterval)
        }
    }
}
