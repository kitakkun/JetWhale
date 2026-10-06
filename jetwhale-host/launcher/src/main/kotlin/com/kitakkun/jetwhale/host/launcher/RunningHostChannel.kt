package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.InstanceJson
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
    override fun isInstanceJsonPublishedBy(pid: Long): Boolean = InstanceJson.read(hostVersionsDirectory)?.pid == pid

    override fun requestBringToFront(): Boolean = InstanceJson.requestBringToFront(hostVersionsDirectory, bringToFrontTimeout)
}
