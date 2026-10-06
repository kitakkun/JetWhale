package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.BringToFrontClient
import com.kitakkun.jetwhale.host.release.HostDirectory
import kotlin.time.Duration

/** How the launcher reaches a host that runs, or that it has just started. */
interface RunningHostChannel {
    /** Whether the host with [pid] has published `instance.json`. */
    fun isInstanceJsonPublishedBy(pid: Long): Boolean

    /** Asks the host that holds `instance.lock` to bring its window to the front. */
    fun requestBringToFront(): Boolean
}

/** Reaches the running host through the `instance.json` it publishes in [hostDirectory]. */
class InstanceJsonChannel(
    private val hostDirectory: HostDirectory,
    private val bringToFrontTimeout: Duration,
) : RunningHostChannel {
    private val bringToFrontClient = BringToFrontClient(hostDirectory)

    override fun isInstanceJsonPublishedBy(pid: Long): Boolean = hostDirectory.readInstanceJson()?.pid == pid

    override fun requestBringToFront(): Boolean = bringToFrontClient.requestBringToFront(bringToFrontTimeout)
}
