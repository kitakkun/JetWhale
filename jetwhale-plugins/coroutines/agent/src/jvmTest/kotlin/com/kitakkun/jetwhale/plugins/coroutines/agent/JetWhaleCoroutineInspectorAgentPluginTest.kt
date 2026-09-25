package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import java.lang.ref.WeakReference as JavaWeakReference

@OptIn(ExperimentalCoroutinesApi::class)
class JetWhaleCoroutineInspectorAgentPluginTest {
    @Test
    fun `a scope the app drops without cancelling it is not kept alive by the inspector`() {
        val inspector = JetWhaleCoroutineInspectorAgentPlugin()
        val job = registerAbandonedScope(inspector)
        assertEquals(listOf("abandoned"), runBlocking { inspector.coroutineTree() }.roots.map(CoroutineNode::name))

        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (job.get() != null && deadline.hasNotPassedNow()) {
            System.gc()
            ByteArray(1 shl 20)
        }

        assertNull(job.get())
        assertEquals(emptyList(), runBlocking { inspector.coroutineTree() }.roots)
    }

    @Test
    fun `with DebugProbes installed a coroutine's detail says it is suspended and where`() {
        DebugProbes.install()
        val inspector = JetWhaleCoroutineInspectorAgentPlugin()
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        scope.launch(CoroutineName("waits-on-gate")) {
            started.complete(Unit)
            gate.await()
        }
        try {
            inspector.register(scope, name = "Application")
            runBlocking { started.await() }
            val root = runBlocking { inspector.coroutineTree() }.roots.single()

            val child = runBlocking { inspector.coroutineDetail(root.children.single().id) }
            val scopeJob = runBlocking { inspector.coroutineDetail(root.id) }

            assertEquals(true, child.found)
            assertEquals("SUSPENDED", child.debugState)
            assertTrue(child.suspensionStack.any { "JetWhaleCoroutineInspectorAgentPluginTest" in it }, child.suspensionStack.joinToString("\n"))
            assertNull(child.stackUnavailableReason)
            assertTrue(scopeJob.stackUnavailableReason.orEmpty().contains("scope's Job"), scopeJob.stackUnavailableReason)
        } finally {
            scope.cancel()
            DebugProbes.uninstall()
        }
    }

    @Test
    fun `without DebugProbes a coroutine's detail says how to get its stack`() {
        if (DebugProbes.isInstalled) DebugProbes.uninstall()
        val inspector = JetWhaleCoroutineInspectorAgentPlugin()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        inspector.register(scope, name = "Application")
        val root = runBlocking { inspector.coroutineTree() }.roots.single()

        val detail = runBlocking { inspector.coroutineDetail(root.id) }

        assertEquals(true, detail.found)
        assertEquals(emptyList(), detail.suspensionStack)
        assertTrue(detail.stackUnavailableReason.orEmpty().contains("DebugProbes.install()"), detail.stackUnavailableReason)
        scope.cancel()
    }

    @Test
    fun `an id no walk has given is reported as not found`() {
        val detail = runBlocking { JetWhaleCoroutineInspectorAgentPlugin().coroutineDetail("c404") }

        assertEquals(false, detail.found)
    }

    /**
     * A scope whose only coroutine waits forever, registered and then let go of. The coroutine is
     * reachable only through the scope's Job, so the whole tree is garbage once nothing else holds
     * the Job. A separate function, so no local of the test still refers to it.
     */
    private fun registerAbandonedScope(inspector: JetWhaleCoroutineInspectorAgentPlugin): JavaWeakReference<Job> {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        scope.launch { awaitCancellation() }
        inspector.register(scope, name = "abandoned")
        return JavaWeakReference(checkNotNull(scope.coroutineContext[Job]))
    }
}
