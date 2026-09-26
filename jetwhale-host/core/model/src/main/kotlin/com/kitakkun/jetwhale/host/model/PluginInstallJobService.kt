package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * Runs plugin installs for the whole host lifetime, independent of any screen: closing the screen
 * that started an install does not stop it. Installs run one at a time, in request order.
 */
interface PluginInstallJobService {
    /** Queued and running installs, and finished ones until they are dismissed, in request order. */
    val jobsFlow: StateFlow<ImmutableList<PluginInstallJob>>

    /**
     * Queues [request], or returns the job already queued or running for the same plugin. A finished
     * job for the same plugin is replaced, which is how a failed install is retried.
     */
    fun enqueue(request: PluginInstallRequest): PluginInstallJob

    /** Queues [request] like [enqueue] and suspends until it has succeeded or failed. */
    suspend fun install(request: PluginInstallRequest): PluginInstallStatus

    /** Cancels the install and cleans up what it downloaded, unless it is past [isCancellable]. */
    fun cancel(jobId: String)

    /** Forgets a finished install. */
    fun dismiss(jobId: String)

    /** Cancels every install that can still be cancelled and waits until each has cleaned up. */
    suspend fun cancelAll()
}
