package com.kitakkun.jetwhale.plugins.background.agent

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Every source's work combined into one snapshot, emitted whenever any source changes. A source
 * whose flow fails is reported unavailable with the failure as the reason, and the others keep
 * reporting.
 */
internal fun observeSnapshots(sources: List<BackgroundWorkSource>): Flow<BackgroundWorkSnapshot> {
    if (sources.isEmpty()) return flowOf(BackgroundWorkSnapshot(sources = emptyList(), items = emptyList()))
    val perSource = sources.map { source ->
        source.observe()
            .map { items -> SourceReading(source.info, items) }
            .catch { failure ->
                val reason = failure.message ?: failure::class.simpleName ?: "the source failed"
                emit(SourceReading(source.info.copy(available = false, unavailableReason = reason), emptyList()))
            }
    }
    return combine(perSource) { readings ->
        BackgroundWorkSnapshot(sources = readings.map(SourceReading::info), items = readings.flatMap(SourceReading::items))
    }.distinctUntilChanged()
}

private class SourceReading(val info: WorkSourceInfo, val items: List<BackgroundWorkItem>)
