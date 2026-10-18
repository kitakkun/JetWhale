package com.kitakkun.jetwhale.plugins.coroutines.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineDumpingTest {
    @Test
    fun `without DebugProbes installed the dump says how to get one`() {
        if (DebugProbes.isInstalled) DebugProbes.uninstall()

        val dump = dumpCoroutines()

        assertEquals("", dump.text)
        assertTrue(dump.unavailableReason.orEmpty().contains("DebugProbes.install()"))
    }

    @Test
    fun `with DebugProbes installed the dump lists a suspended coroutine by name`() {
        DebugProbes.install()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val job = CoroutineScope(Dispatchers.Default).launch(CoroutineName("stuck-on-gate")) {
            started.complete(Unit)
            gate.await()
        }
        try {
            runBlocking { started.await() }

            val dump = dumpCoroutines()

            assertNull(dump.unavailableReason)
            assertTrue("stuck-on-gate" in dump.text, dump.text)
        } finally {
            job.cancel()
            DebugProbes.uninstall()
        }
    }
}
