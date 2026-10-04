package com.kitakkun.jetwhale.plugins.coroutines.agent.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.kitakkun.jetwhale.plugins.coroutines.agent.JetWhaleCoroutineInspectorAgentPlugin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `coroutines that state producers and subcompositions start are listed under the registered name`() = runTest {
        composing({ ScreenWithStateProducersAndSubcomposition() }) {
            val names = inspector.registeredRoots()["Compose"]?.descendants().orEmpty().mapNotNull(Job::nameOrNull)

            assertEquals(setOf("produced", "collected", "item-poll"), names.toSet())
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

    @Test
    fun `leaving composition keeps a newer registration under the same name`() = runTest {
        var tracked by mutableStateOf(true)
        val otherWindow = Job()
        composing({ ScreenWithCoroutines(tracked = tracked) }) {
            inspector.register(otherWindow, name = "Compose")
            Snapshot.withMutableSnapshot { tracked = false }
            it.settle()

            assertTrue(inspector.registeredRoots()["Compose"] === otherWindow)
        }
        otherWindow.cancel()
    }

    @Test
    fun `leaving composition keeps the root while another composition of the same Recomposer tracks it`() = runTest {
        var otherViewShown by mutableStateOf(true)
        composing({
            inspector.TrackCompositionCoroutines(name = "Compose")
            if (otherViewShown) Subcomposition { inspector.TrackCompositionCoroutines(name = "Compose") }
        }) {
            Snapshot.withMutableSnapshot { otherViewShown = false }
            it.settle()

            assertNotNull(inspector.registeredRoots()["Compose"])
        }
    }

    @Composable
    private fun ScreenWithCoroutines(tracked: Boolean) {
        if (tracked) inspector.TrackCompositionCoroutines(name = "Compose")
        LaunchedEffect(Unit) { withContext(CoroutineName("polling")) { awaitCancellation() } }
        val scope = rememberCoroutineScope()
        LaunchedEffect(scope) { scope.launch(CoroutineName("button-work")) { awaitCancellation() } }
    }

    @Composable
    private fun ScreenWithStateProducersAndSubcomposition() {
        inspector.TrackCompositionCoroutines(name = "Compose")
        produceState(0) { withContext(CoroutineName("produced")) { awaitCancellation() } }
        remember { MutableStateFlow(0) }.collectAsState(context = CoroutineName("collected"))
        Subcomposition { LaunchedEffect(Unit) { withContext(CoroutineName("item-poll")) { awaitCancellation() } } }
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

/** Composes [content] the way a lazy list item or a `ComposeView` inside `AndroidView` is: under this composition's context. */
@Composable
private fun Subcomposition(content: @Composable () -> Unit) {
    val parent = rememberCompositionContext()
    DisposableEffect(parent) {
        val subcomposition = Composition(UnitApplier, parent)
        subcomposition.setContent(content)
        onDispose(subcomposition::dispose)
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
    return map.entries.associate { (name, root) ->
        val reference = checkNotNull(root).javaClass.getDeclaredField("job").apply { isAccessible = true }.get(root)
        name as String to reference.javaClass.getMethod("get").invoke(reference) as Job?
    }
}

private object UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
