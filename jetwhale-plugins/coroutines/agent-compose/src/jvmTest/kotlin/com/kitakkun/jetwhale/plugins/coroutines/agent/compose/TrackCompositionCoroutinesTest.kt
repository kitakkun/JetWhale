package com.kitakkun.jetwhale.plugins.coroutines.agent.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.kitakkun.jetwhale.plugins.coroutines.agent.JetWhaleCoroutineInspectorAgentPlugin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackCompositionCoroutinesTest {
    private val inspector = JetWhaleCoroutineInspectorAgentPlugin()

    @Test
    fun `coroutines a composition starts are listed under the registered name`() = runTest {
        composing({ ScreenWithCoroutines(tracked = true) }) {
            val names = inspector.registeredRoots()["Compose"]?.descendants().orEmpty().mapNotNull(Job::nameOrNull)

            assertEquals(setOf("polling", "button-work"), names.toSet())
        }
    }

    @Test
    fun `nothing of the tracking itself is left in the tree`() = runTest {
        composing({ ScreenWithCoroutines(tracked = true) }) {
            val root = inspector.registeredRoots()["Compose"]

            // The screen's LaunchedEffect and its remembered scope; no helper coroutine of the tracking.
            assertEquals(2, root?.children?.count())
        }
    }

    @Test
    fun `leaving composition stops showing the composition`() = runTest {
        var tracked by mutableStateOf(true)
        composing({ ScreenWithCoroutines(tracked = tracked) }) {
            Snapshot.withMutableSnapshot { tracked = false }
            it.settle()

            assertNull(inspector.registeredRoots()["Compose"])
        }
    }

    @Composable
    private fun ScreenWithCoroutines(tracked: Boolean) {
        if (tracked) inspector.TrackCompositionCoroutines(name = "Compose")
        LaunchedEffect(Unit) { withContext(CoroutineName("polling")) { awaitCancellation() } }
        val scope = rememberCoroutineScope()
        LaunchedEffect(scope) { scope.launch(CoroutineName("button-work")) { awaitCancellation() } }
    }

    private suspend fun TestScope.composing(content: @Composable () -> Unit, check: suspend (FrameDriver) -> Unit) {
        val clock = BroadcastFrameClock()
        withContext(clock) {
            val recomposer = Recomposer(coroutineContext)
            val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
            val composition = Composition(UnitApplier, recomposer)
            composition.setContent(content)
            val driver = FrameDriver(clock)
            driver.settle()
            try {
                check(driver)
            } finally {
                composition.dispose()
                recomposer.cancel()
                runner.join()
            }
        }
    }
}

private class FrameDriver(private val clock: BroadcastFrameClock) {
    suspend fun settle() {
        repeat(FRAMES_TO_SETTLE) {
            Snapshot.sendApplyNotifications()
            clock.sendFrame(0L)
            yield()
        }
    }
}

private const val FRAMES_TO_SETTLE = 5

/** A coroutine's job is also its scope, which carries the name; a plain `Job()` has none. */
private fun Job.nameOrNull(): String? = (this as? CoroutineScope)?.coroutineContext?.get(CoroutineName)?.name

private fun Job.descendants(): List<Job> = children.flatMap { listOf(it) + it.descendants() }.toList()

/**
 * The jobs the inspector currently shows, by name. The inspector keeps them private and hands them
 * to the host only over messaging, which a unit test outside its module cannot drive, so they are
 * read reflectively.
 */
private fun JetWhaleCoroutineInspectorAgentPlugin.registeredRoots(): Map<String, Job?> {
    val field = JetWhaleCoroutineInspectorAgentPlugin::class.java.getDeclaredField("roots").apply { isAccessible = true }
    val roots = field.get(this)
    val map = roots.javaClass.getMethod("get").invoke(roots) as Map<*, *>
    return map.entries.associate { (name, reference) -> name as String to reference?.let { it.javaClass.getMethod("get").invoke(it) as Job? } }
}

private object UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
