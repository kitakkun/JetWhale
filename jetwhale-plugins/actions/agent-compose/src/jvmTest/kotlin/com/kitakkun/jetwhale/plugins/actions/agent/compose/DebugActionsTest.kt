package com.kitakkun.jetwhale.plugins.actions.agent.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.actions.agent.JetWhaleDebugActionsAgentPlugin
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalJetWhaleApi::class)
class DebugActionsTest {
    private val plugin = JetWhaleDebugActionsAgentPlugin()

    @Test
    fun `actions exist while the composable is shown and go when it leaves`() = runTest {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val recomposition = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(NothingApplier(), recomposer)
        var screenShown by mutableStateOf(true)

        composition.setContent {
            if (screenShown) plugin.DebugActions { action("Fill test card") { run { } } }
        }
        assertEquals(listOf("Fill test card"), plugin.catalog().actions.map(ActionDescriptor::id))

        screenShown = false
        // The recomposer has to be waiting for a frame before one is sent, or the frame is lost.
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
        frameClock.sendFrame(0L)
        testScheduler.runCurrent()

        assertEquals(emptyList(), plugin.catalog().actions.map(ActionDescriptor::id))
        composition.dispose()
        recomposer.cancel()
        recomposition.join()
    }

    @Test
    fun `changing a key declares the actions again`() = runTest {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val recomposition = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(NothingApplier(), recomposer)
        var card by mutableStateOf("visa")

        composition.setContent {
            plugin.DebugActions(card) { action("Fill $card") { run { } } }
        }
        card = "amex"
        // The recomposer has to be waiting for a frame before one is sent, or the frame is lost.
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
        frameClock.sendFrame(0L)
        testScheduler.runCurrent()

        assertEquals(listOf("Fill amex"), plugin.catalog().actions.map(ActionDescriptor::id))
        composition.dispose()
        recomposer.cancel()
        recomposition.join()
    }
}

/** A composition that emits no nodes: only effects run, which is all these tests look at. */
private class NothingApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun remove(index: Int, count: Int) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun onClear() = Unit
}
