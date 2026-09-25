package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.coroutines.agent.JetWhaleCoroutineInspectorAgentPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A misbehavior the Coroutine Inspector is meant to find, as a real app would have it.
 *
 * @property lookIn Where in the inspector it shows up.
 */
enum class CoroutineScenario(val title: String, val simulates: String, val lookIn: String) {
    NeverReturningRequest(
        title = "A request that never returns",
        simulates = "Waits for a response that nothing will ever send — the classic stuck coroutine.",
        lookIn = "Coroutines → click “awaiting-response” → Where it waits",
    ),
    LeakedPolling(
        title = "Polling started on screen entry and never stopped",
        simulates = "Each start leaks a poller that wakes every second, as if a screen forgot to cancel its job.",
        lookIn = "Coroutines → age “≥ 10 s” → the “poller” rows pile up",
    ),
    MainThreadParsing(
        title = "Heavy parsing on the main thread",
        simulates = "Parses a feed on Main for 300 ms every 2 seconds; each run drops frames.",
        lookIn = "Dispatchers → Long runs → “parse-feed” on Main",
    ),
    QueuedUploads(
        title = "Uploads queued behind a small thread pool",
        simulates = "Queues 6 uploads of 500 ms on a dispatcher limited to 2 threads, so most of them wait their turn.",
        lookIn = "Dispatchers → “Uploads (2 threads)”: Waiting and Wait max",
    ),
    NestedSync(
        title = "A sync with nested downloads",
        simulates = "A parent “sync” with three “download” children, all waiting.",
        lookIn = "Coroutines → expand “sync” → select it → Below it",
    ),
    SlowCancellation(
        title = "Cleanup that ignores cancellation",
        simulates = "Runs until stopped, then spends 5 seconds in cleanup that cannot be cancelled.",
        lookIn = "Press Stop, then Coroutines → state “Cancelling” for 5 s",
    ),
    DuplicateCollection(
        title = "The same flow collected twice",
        simulates = "Two collectors of the “ticker” flow, as when a screen collects it in two places.",
        lookIn = "Flows → “ticker”: Collectors 2, and every value twice",
    ),
}

/**
 * Starts and stops the [CoroutineScenario]s in a scope registered with the inspector as
 * [SCOPE_NAME], and keeps what each one has running.
 */
class CoroutineDemo(private val inspector: JetWhaleCoroutineInspectorAgentPlugin) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("demo"))
    private val trackedMain = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)
    private val uploads = inspector.track(Dispatchers.Default.limitedParallelism(2), name = "Uploads (2 threads)", longRunThreshold = 400.milliseconds)
    private val ticker: Flow<Int> = inspector.track(
        flow {
            var tick = 0
            while (true) {
                emit(tick++)
                delay(500.milliseconds)
            }
        },
        name = "ticker",
    )
    private var pollers = 0

    private val mutableRunning = MutableStateFlow(emptyMap<CoroutineScenario, Set<Job>>())

    /** The coroutines each scenario started that have not finished yet. */
    val running: StateFlow<Map<CoroutineScenario, Set<Job>>> = mutableRunning.asStateFlow()

    fun registerScope() {
        inspector.register(scope, name = SCOPE_NAME)
    }

    fun start(scenario: CoroutineScenario) {
        when (scenario) {
            CoroutineScenario.NeverReturningRequest -> launch(scenario, "awaiting-response", EmptyCoroutineContext) { CompletableDeferred<String>().await() }

            CoroutineScenario.LeakedPolling -> launch(scenario, "poller-${++pollers}", EmptyCoroutineContext) {
                while (true) delay(1.seconds)
            }

            CoroutineScenario.MainThreadParsing -> launch(scenario, "parse-feed-loop", EmptyCoroutineContext) {
                while (true) {
                    withContext(trackedMain + CoroutineName("parse-feed")) { busyFor(300.milliseconds) }
                    delay(2.seconds)
                }
            }

            // One name for all, as a real app's uploads would share one: the long runs then group
            // into a single row.
            CoroutineScenario.QueuedUploads -> repeat(6) {
                launch(scenario, "upload", uploads) { busyFor(500.milliseconds) }
            }

            CoroutineScenario.NestedSync -> launch(scenario, "sync", EmptyCoroutineContext) {
                repeat(3) { index -> launch(CoroutineName("download-${index + 1}")) { awaitCancellation() } }
            }

            CoroutineScenario.SlowCancellation -> launch(scenario, "cleanup-on-cancel", EmptyCoroutineContext) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { delay(5.seconds) }
                }
            }

            CoroutineScenario.DuplicateCollection -> repeat(2) { index ->
                launch(scenario, "ticker-collector-${index + 1}", EmptyCoroutineContext) { ticker.collect {} }
            }
        }
    }

    fun stop(scenario: CoroutineScenario) {
        mutableRunning.value[scenario].orEmpty().forEach(Job::cancel)
    }

    fun stopAll() {
        mutableRunning.value.values.flatten().forEach(Job::cancel)
    }

    private fun launch(scenario: CoroutineScenario, name: String, context: CoroutineContext, block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(CoroutineName(name) + context, block = block)
        mutableRunning.update { it + (scenario to it[scenario].orEmpty() + job) }
        job.invokeOnCompletion { mutableRunning.update { it + (scenario to it[scenario].orEmpty() - job) } }
    }

    companion object {
        const val SCOPE_NAME: String = "Demo"
    }
}

/** Busy work on purpose: the point is a task that holds its thread, as a heavy parse would. */
private fun busyFor(duration: Duration) {
    val start = TimeSource.Monotonic.markNow()
    while (start.elapsedNow() < duration) Unit
}
