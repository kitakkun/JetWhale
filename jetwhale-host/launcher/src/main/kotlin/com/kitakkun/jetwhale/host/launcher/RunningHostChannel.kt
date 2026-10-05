package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostInstanceRecord
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import kotlin.time.Duration

/** How the launcher reaches a host that runs, or that it has just started. */
interface RunningHostChannel {
    /** Whether the host with [pid] has published its instance record. */
    fun isPublishedBy(pid: Long): Boolean

    /** Asks the host that holds `instance.lock` to bring its window forward. */
    fun requestActivation(): Boolean
}

/** Reaches the running host through the instance record it publishes in [hostVersionsDirectory]. */
class InstanceRecordChannel(
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val activationTimeout: Duration,
) : RunningHostChannel {
    override fun isPublishedBy(pid: Long): Boolean = HostInstanceRecord.read(hostVersionsDirectory)?.pid == pid

    override fun requestActivation(): Boolean = HostInstanceRecord.requestActivation(hostVersionsDirectory, activationTimeout)
}
