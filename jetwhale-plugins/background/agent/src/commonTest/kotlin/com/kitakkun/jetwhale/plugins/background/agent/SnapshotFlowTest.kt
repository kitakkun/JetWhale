package com.kitakkun.jetwhale.plugins.background.agent

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotFlowTest {
    @Test
    fun `work from every source is combined into one snapshot`() = runTest {
        val first = FakeSource("First", MutableStateFlow(listOf(item("First", "a"))))
        val second = FakeSource("Second", MutableStateFlow(listOf(item("Second", "b"))))

        val snapshot = observeSnapshots(listOf(first, second)).first()

        assertEquals(listOf("First", "Second"), snapshot.sources.map(WorkSourceInfo::name))
        assertEquals(listOf("a", "b"), snapshot.items.map(BackgroundWorkItem::id))
    }

    @Test
    fun `a change in one source emits a new snapshot`() = runTest {
        val changing = MutableStateFlow(listOf(item("First", "a")))
        val snapshots = mutableListOf<List<String>>()
        val collection = launch {
            observeSnapshots(listOf(FakeSource("First", changing))).take(2).toList().mapTo(snapshots) { it.items.map(BackgroundWorkItem::id) }
        }
        testScheduler.runCurrent()

        changing.value = listOf(item("First", "a"), item("First", "b"))
        collection.join()

        assertEquals(listOf(listOf("a"), listOf("a", "b")), snapshots)
    }

    @Test
    fun `a failing source is reported unavailable and the others keep reporting`() = runTest {
        val failing = object : FakeSourceBase("Broken") {
            override fun observe(): Flow<List<BackgroundWorkItem>> = flow { throw IllegalStateException("scheduler gone") }
        }
        val healthy = FakeSource("Healthy", MutableStateFlow(listOf(item("Healthy", "a"))))

        val snapshot = observeSnapshots(listOf(failing, healthy)).first()

        val broken = snapshot.sources.single { it.name == "Broken" }
        assertEquals(false, broken.available)
        assertEquals("scheduler gone", broken.unavailableReason)
        assertEquals(listOf("a"), snapshot.items.map(BackgroundWorkItem::id))
    }

    @Test
    fun `no sources is an empty snapshot`() = runTest {
        val snapshot = observeSnapshots(emptyList()).first()

        assertEquals(emptyList(), snapshot.sources)
        assertEquals(emptyList(), snapshot.items)
    }
}

private abstract class FakeSourceBase(name: String) : BackgroundWorkSource {
    override val info = WorkSourceInfo(name = name, available = true, unavailableReason = null, supportsCancelByTag = false, supportsCancelByUniqueName = false)

    override suspend fun cancel(target: CancelTarget): String = "cancelled"

    override suspend fun runNow(id: String): String = "ran"
}

private class FakeSource(name: String, private val work: Flow<List<BackgroundWorkItem>>) : FakeSourceBase(name) {
    override fun observe(): Flow<List<BackgroundWorkItem>> = work
}

internal fun item(source: String, id: String): BackgroundWorkItem = BackgroundWorkItem(
    source = source,
    id = id,
    name = "Worker $id",
    state = WorkState.Enqueued,
    tags = emptyList(),
    uniqueName = null,
    runAttemptCount = null,
    constraints = emptyList(),
    nextRunEpochMillis = null,
    periodMillis = null,
    flexMillis = null,
    progress = emptyMap(),
    output = emptyMap(),
    stopReason = null,
    details = emptyMap(),
    canCancel = true,
    canRunNow = false,
    runNowHint = null,
)
