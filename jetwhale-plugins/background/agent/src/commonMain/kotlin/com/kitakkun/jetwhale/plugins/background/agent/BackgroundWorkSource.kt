package com.kitakkun.jetwhale.plugins.background.agent

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import kotlinx.coroutines.flow.Flow

/**
 * A scheduler whose work the host may see and act on: WorkManager, JobScheduler, BGTaskScheduler,
 * or one of the app's own. Implement it to show a scheduler the platform defaults do not cover.
 */
interface BackgroundWorkSource {
    /** Names the source in the host and says what it supports; the name must be unique. */
    val info: WorkSourceInfo

    /** The work the source holds, emitted again whenever it changes. */
    fun observe(): Flow<List<BackgroundWorkItem>>

    /**
     * Cancels [target] and returns what was done, in a sentence for the host.
     *
     * @throws Exception when the source cannot cancel it; the message reaches the host.
     */
    suspend fun cancel(target: CancelTarget): String

    /**
     * Runs the work [id] as soon as the source allows and returns what was actually done, which
     * may be less than a run right now (a copy without constraints, say).
     *
     * @throws Exception when the source cannot run it; the message reaches the host.
     */
    suspend fun runNow(id: String): String

    companion object
}

/**
 * The schedulers every app on this platform has: on Android JobScheduler, the next alarm clock
 * and the app's running services; on iOS BGTaskScheduler. None elsewhere. WorkManager is not among
 * them, since not every app depends on it; add it with the WorkManager adapter artifact.
 */
expect fun BackgroundWorkSource.Companion.platformDefaults(): List<BackgroundWorkSource>
