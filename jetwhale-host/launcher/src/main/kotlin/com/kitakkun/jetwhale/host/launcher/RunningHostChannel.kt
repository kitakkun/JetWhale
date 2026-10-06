package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.BringToFrontClient
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import kotlin.time.Duration

/** How the launcher reaches a host that runs, or that it has just started. */
interface RunningHostChannel {
    /** Whether the host with [pid] has published `instance.json`. */
    fun isInstanceJsonPublishedBy(pid: Long): Boolean

    /** Asks the host that holds `instance.lock` to bring its window to the front. */
    fun requestBringToFront(): Boolean
}

/** Reaches the running host through the `instance.json` it publishes in [hostVersionsDirectory]. */
class InstanceJsonChannel(
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val bringToFrontTimeout: Duration,
) : RunningHostChannel {
    private val bringToFrontClient = BringToFrontClient(hostVersionsDirectory)

    override fun isInstanceJsonPublishedBy(pid: Long): Boolean = hostVersionsDirectory.readInstanceJson()?.pid == pid

    override fun requestBringToFront(): Boolean = bringToFrontClient.requestBringToFront(bringToFrontTimeout)
}
