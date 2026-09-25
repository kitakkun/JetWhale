package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.LongRun
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutineInspectorPanesTest {
    @Test
    fun `long runs are grouped by who held the thread with the longest holder first`() {
        val runs = listOf(
            DispatcherLongRun("Main", LongRun(atEpochMillis = 1, durationMillis = 300.0, coroutineName = "main-blocker")),
            DispatcherLongRun("Main", LongRun(atEpochMillis = 2, durationMillis = 20.0, coroutineName = "render")),
            DispatcherLongRun("Main", LongRun(atEpochMillis = 3, durationMillis = 310.0, coroutineName = "main-blocker")),
        )

        val holders = summarizeLongRuns(runs)

        assertEquals(listOf("main-blocker", "render"), holders.map(LongRunHolder::coroutineName))
        assertEquals(LongRunHolder(dispatcher = "Main", coroutineName = "main-blocker", count = 2, longestMillis = 310.0, totalMillis = 610.0, lastAtEpochMillis = 3), holders.first())
    }

    @Test
    fun `the first app frame skips the coroutines library and the platform`() {
        val stack = listOf(
            "kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:160)",
            "java.lang.Thread.run(Thread.java:1583)",
            "com.example.sync.Poller${'$'}poll${'$'}1.invokeSuspend(Poller.kt:12)",
            "kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)",
        )

        assertEquals("com.example.sync.Poller${'$'}poll${'$'}1.invokeSuspend(Poller.kt:12)", firstAppFrame(stack))
    }

    @Test
    fun `a stack of library frames only has no app frame`() {
        assertEquals(null, firstAppFrame(listOf("kotlinx.coroutines.DelayKt.delay(Delay.kt:1)")))
    }

    @Test
    fun `a dump filter keeps the header and only the coroutines that mention the text`() {
        val dump = listOf(
            "Coroutines dump 2026/09/26 00:17:56",
            "Coroutine StandaloneCoroutine{Active}@1, state: SUSPENDED\n at io.ktor.client.engine.cio.CIOEngine${'$'}1.invokeSuspend(CIOEngine.kt:75)",
            "Coroutine StandaloneCoroutine{Active}@2, state: SUSPENDED\n at com.example.demo.CoroutineDemo${'$'}startStuckCoroutine${'$'}1.invokeSuspend(CoroutineDemo.kt:43)",
        ).joinToString("\n\n")

        val filtered = filterDump(dump, "coroutinedemo")

        assertEquals(1, countDumpedCoroutines(filtered))
        assertEquals(2, countDumpedCoroutines(dump))
        assertEquals(true, filtered.startsWith("Coroutines dump"))
        assertEquals(true, "CoroutineDemo.kt:43" in filtered)
    }
}
