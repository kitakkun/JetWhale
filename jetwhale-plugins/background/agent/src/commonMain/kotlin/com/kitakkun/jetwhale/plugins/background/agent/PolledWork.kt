package com.kitakkun.jetwhale.plugins.background.agent

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * How often a scheduler with no change notifications is read again. Pending jobs and task
 * requests change on the scale of seconds, and each read is a cheap system call.
 */
internal const val POLL_INTERVAL_MILLIS: Long = 2_000

/**
 * The work [read] returns, read every [intervalMillis] for a scheduler that cannot notify of
 * changes, and emitted only when it differs from the last read.
 */
internal fun pollWork(intervalMillis: Long, read: suspend () -> List<BackgroundWorkItem>): Flow<List<BackgroundWorkItem>> = flow {
    while (true) {
        emit(read())
        delay(intervalMillis)
    }
}.distinctUntilChanged()
