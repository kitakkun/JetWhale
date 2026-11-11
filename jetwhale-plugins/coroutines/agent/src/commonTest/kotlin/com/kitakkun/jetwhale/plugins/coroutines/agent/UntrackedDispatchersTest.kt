package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.UntrackedDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class UntrackedDispatchersTest {
    /** A test dispatcher that prints a fixed name, as `Dispatchers.Default` prints its own. */
    private class NamedDispatcher(private val name: String, private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = delegate.dispatch(context, block)

        override fun toString(): String = name
    }

    @Test
    fun `coroutines on untracked dispatchers are counted by state and tracked ones are left out`() = runTest {
        val root = Job()
        val app = CoroutineScope(root)
        val background = NamedDispatcher("Background", StandardTestDispatcher(testScheduler))
        val disk = NamedDispatcher("Disk", StandardTestDispatcher(testScheduler))
        val main = TrackedDispatcher(NamedDispatcher("Main", StandardTestDispatcher(testScheduler)), DispatcherRecorder(name = "Main", longRunThreshold = 1.hours))
        val gate = CompletableDeferred<Unit>()
        app.launch(background) {
            launch { gate.await() }
            gate.await()
        }
        app.launch(disk, start = CoroutineStart.LAZY) { gate.await() }
        app.launch(main) { gate.await() }
        runCurrent()

        val untracked = untrackedDispatchers(listOf(root), nodeLimit = 100)

        assertEquals(
            listOf(
                UntrackedDispatcher(name = "Background", coroutinesByState = mapOf(CoroutineState.Active to 2)),
                UntrackedDispatcher(name = "Disk", coroutinesByState = mapOf(CoroutineState.New to 1)),
            ),
            untracked,
        )
        gate.complete(Unit)
    }

    @Test
    fun `a coroutine below two overlapping registered scopes is counted once`() = runTest {
        val root = Job()
        val gate = CompletableDeferred<Unit>()
        val worker = CoroutineScope(root).launch(NamedDispatcher("Background", StandardTestDispatcher(testScheduler))) { gate.await() }
        runCurrent()

        val untracked = untrackedDispatchers(listOf(root, worker), nodeLimit = 100)

        assertEquals(listOf(UntrackedDispatcher(name = "Background", coroutinesByState = mapOf(CoroutineState.Active to 1))), untracked)
        gate.complete(Unit)
    }
}
