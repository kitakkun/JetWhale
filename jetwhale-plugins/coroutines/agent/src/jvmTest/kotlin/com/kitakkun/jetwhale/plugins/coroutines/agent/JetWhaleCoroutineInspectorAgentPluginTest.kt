package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import java.lang.ref.WeakReference as JavaWeakReference

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
