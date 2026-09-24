package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JobTreeWalkerTest {
    @Test
    fun `the tree shows each coroutine below a registered job with its name and state`() = runTest {
        val root = Job()
        val app = CoroutineScope(root + StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        app.launch(CoroutineName("loader")) {
            launch(CoroutineName("child")) { gate.await() }
            gate.await()
        }
        app.launch(CoroutineName("lazy"), start = CoroutineStart.LAZY) { gate.await() }
        runCurrent()

        val tree = JobTreeWalker(nodeLimit = 100).walk(mapOf("app" to root), capturedAtEpochMillis = 0)

        val appNode = tree.roots.single()
        assertEquals("app", appNode.name)
        assertEquals(listOf("loader" to CoroutineState.Active, "lazy" to CoroutineState.New), appNode.children.map { it.name to it.state })
        assertEquals(listOf("child"), appNode.children.first().children.map(CoroutineNode::name))
        assertEquals(4, tree.coroutineCount)
        gate.complete(Unit)
        root.cancel()
    }

    @Test
    fun `a coroutine keeps its id from one walk to the next`() = runTest {
        val root = Job()
        val gate = CompletableDeferred<Unit>()
        CoroutineScope(root + StandardTestDispatcher(testScheduler)).launch(CoroutineName("worker")) { gate.await() }
        runCurrent()
        val walker = JobTreeWalker(nodeLimit = 100)

        val first = walker.walk(mapOf("app" to root), capturedAtEpochMillis = 0).roots.single().children.single().id
        val second = walker.walk(mapOf("app" to root), capturedAtEpochMillis = 0).roots.single().children.single().id

        assertEquals(first, second)
        gate.complete(Unit)
        root.cancel()
    }

    @Test
    fun `a walk stops at the node limit and says it was cut short`() = runTest {
        val root = Job()
        val gate = CompletableDeferred<Unit>()
        val app = CoroutineScope(root + StandardTestDispatcher(testScheduler))
        repeat(10) { app.launch { gate.await() } }
        runCurrent()

        val tree = JobTreeWalker(nodeLimit = 4).walk(mapOf("app" to root), capturedAtEpochMillis = 0)

        assertEquals(4, tree.coroutineCount)
        assertTrue(tree.truncated)
        gate.complete(Unit)
        root.cancel()
    }

    @Test
    fun `job states map to the coroutine states the host shows`() = runTest {
        val completed = Job().apply { complete() }
        val cancelled = Job().apply { cancel() }
        val cancelling = Job()
        val gate = CompletableDeferred<Unit>()
        CoroutineScope(cancelling + StandardTestDispatcher(testScheduler)).launch { withContext(NonCancellable) { gate.await() } }
        runCurrent()
        cancelling.cancel()

        assertEquals(CoroutineState.Active, Job().coroutineState())
        assertEquals(CoroutineState.Completed, completed.coroutineState())
        assertEquals(CoroutineState.Cancelled, cancelled.coroutineState())
        assertEquals(CoroutineState.Cancelling, cancelling.coroutineState())
        gate.complete(Unit)
    }
}
