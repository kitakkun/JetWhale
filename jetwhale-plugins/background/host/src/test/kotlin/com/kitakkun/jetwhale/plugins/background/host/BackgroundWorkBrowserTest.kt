package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundWorkBrowserTest {
    private val client = FakeBackgroundWorkClient(
        listOf(
            workItem("WorkManager", "sync", WorkState.Enqueued, tags = listOf("sync"), canRunNow = true),
            workItem("WorkManager", "upload", WorkState.Failed, tags = listOf("nightly"), canRunNow = true),
            workItem("JobScheduler", "cleanup", WorkState.Scheduled, tags = emptyList(), canRunNow = false),
        ),
    )

    // The fake answers without suspending, so every launched call has finished by the time launch returns.
    private val browser = BackgroundWorkBrowser(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `the pending filter hides finished work`() {
        runBlocking { browser.load() }

        browser.changeFilter(WorkFilter(states = StateScope.Pending.states, query = ""))

        assertEquals(listOf("sync", "cleanup"), browser.visibleItems.map(BackgroundWorkItem::id))
    }

    @Test
    fun `the text filter matches tags as well as names`() {
        runBlocking { browser.load() }

        browser.changeFilter(WorkFilter(states = emptySet(), query = "NIGHTLY"))

        assertEquals(listOf("upload"), browser.visibleItems.map(BackgroundWorkItem::id))
    }

    @Test
    fun `cancelling reloads and the history records the new state`() {
        runBlocking { browser.load() }

        browser.cancel("WorkManager", CancelTarget.ById("sync"))

        val key = WorkKey("WorkManager", "sync")
        assertEquals(listOf(WorkState.Enqueued, WorkState.Cancelled), browser.history.getValue(key).map(StateTransition::state))
        assertEquals(false, browser.status?.isError)
    }

    @Test
    fun `a refused run is reported as an error`() {
        runBlocking { browser.load() }

        browser.runNow(WorkKey("JobScheduler", "cleanup"))

        assertEquals(true, browser.status?.isError)
    }

    @Test
    fun `the selection follows the work into newer snapshots`() {
        runBlocking { browser.load() }
        browser.select(WorkKey("WorkManager", "sync"))

        browser.cancel("WorkManager", CancelTarget.ById("sync"))

        assertEquals(WorkState.Cancelled, browser.selectedItem?.state)
    }
}
