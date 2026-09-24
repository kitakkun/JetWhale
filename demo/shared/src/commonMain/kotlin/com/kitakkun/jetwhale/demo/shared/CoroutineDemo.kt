package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.coroutines.agent.JetWhaleCoroutineInspectorAgentPlugin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The misbehaving coroutines the Coroutine Inspector is meant to find, started on demand from the
 * demo's Coroutines tab: one that waits forever, pollers that are never stopped, a task that holds
 * the main thread, and a flow to watch.
 */
class CoroutineDemo(private val inspector: JetWhaleCoroutineInspectorAgentPlugin) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("demo"))
    private val trackedMain = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)
    private var leakedPollers = 0

    val ticker: Flow<Int> = inspector.track(
        flow {
            var tick = 0
            while (true) {
                emit(tick++)
                delay(500.milliseconds)
            }
        },
        name = "ticker",
    )

    fun registerScope() {
        inspector.register(scope, name = "Demo")
    }

    fun startStuckCoroutine() {
        scope.launch(CoroutineName("waits-forever")) { awaitCancellation() }
    }

    fun leakPoller() {
        leakedPollers++
        scope.launch(CoroutineName("leaked-poller-$leakedPollers")) {
            while (true) delay(1_000.milliseconds)
        }
    }

    fun blockMainThread() {
        scope.launch(trackedMain + CoroutineName("main-blocker")) {
            val start = TimeSource.Monotonic.markNow()
            // Busy work on purpose: the point is a task that holds the main thread, as a heavy parse would.
            while (start.elapsedNow() < 300.milliseconds) Unit
        }
    }

    fun cancelAll() {
        scope.coroutineContext[Job]?.children?.forEach(Job::cancel)
    }
}
