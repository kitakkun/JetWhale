package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.coroutines.protocol.COROUTINES_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DumpCoroutines
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetCoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetCoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetDispatcherStats
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetTrackedFlows
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowInfo
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Agent plugin that shows the host what the app's coroutines are doing: the coroutines below the
 * scopes the app registers, how busy the dispatchers it tracks are, and what its tracked flows
 * emit. Nothing is instrumented automatically; the app points the plugin at what it wants to see.
 *
 * ```kotlin
 * val inspector = JetWhaleCoroutineInspectorAgentPlugin()
 * startJetWhale { plugins { register(inspector) } }
 *
 * inspector.register(applicationScope, name = "Application")
 * val main = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)
 * val prices = inspector.track(repository.prices, name = "prices")
 * ```
 *
 * The host asks for everything while someone is looking; when no one is, tracking costs a clock
 * read and a few counter updates per dispatch or emission. While a host has the plugin active, the
 * plugin also notes new coroutines every couple of seconds, so their ages are right by the time
 * someone opens the tree.
 */
@OptIn(ExperimentalAtomicApi::class)
class JetWhaleCoroutineInspectorAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = COROUTINES_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    // Held weakly: a scope dropped without being cancelled leaves coroutines that only its Job
    // still reaches, and the inspector must not be what keeps them — and all they capture — alive.
    private val roots = AtomicReference(emptyMap<String, WeakReference<Job>>())
    private val dispatchers = AtomicReference(emptyList<DispatcherRecorder>())
    private val flows = AtomicReference(emptyMap<String, FlowRecorder>())
    private val walker = JobTreeWalker(nodeLimit = MAX_TREE_NODES, timeSource = TimeSource.Monotonic)
    private val walkLock = Mutex()
    private var sighting: Job? = null

    /**
     * Shows the coroutines of [scope] under [name]. A scope whose job completes is dropped by
     * itself; one registered again under the same name replaces the previous one. The inspector
     * does not keep the scope alive: one the app drops without cancelling it disappears once it is
     * garbage-collected (on Kotlin/JS and Kotlin/Wasm, once it completes).
     *
     * @throws IllegalArgumentException when [scope] has no `Job`, e.g. `GlobalScope`.
     */
    fun register(scope: CoroutineScope, name: String) {
        val job = requireNotNull(scope.coroutineContext[Job]) { "the scope '$name' has no Job to walk" }
        register(job, name)
    }

    /** Shows the coroutines below [job] under [name]; see the scope overload. */
    fun register(job: Job, name: String) {
        walker.registered(job)
        roots.updateAndGet { it + (name to WeakReference(job)) }
        job.invokeOnCompletion { roots.updateAndGet { current -> if (current[name]?.get() === job) current - name else current } }
    }

    fun unregister(name: String) {
        roots.updateAndGet { it - name }
    }

    /**
     * Returns [dispatcher] wrapped so that the host sees how many tasks wait on it, how long they
     * wait and how long each holds its thread; a task running [longRunThreshold] or longer is
     * listed with the name of its coroutine. Use the returned dispatcher where the app used
     * [dispatcher].
     *
     * The wrapper is a plain `CoroutineDispatcher` that dispatches every task so that every task is
     * timed: `Dispatchers.Main.immediate` loses its immediate execution, and the dispatcher's own
     * timer for `delay` is not carried over, so delays are timed by the coroutines library's
     * default timer and then dispatched here.
     *
     * A tracked dispatcher's record is kept for the life of the plugin, so track each dispatcher
     * once, under a name for its purpose.
     *
     * @throws IllegalArgumentException when [name] is already tracked, or [dispatcher] is
     *   `Dispatchers.Unconfined`, which runs tasks in place and has nothing to time.
     */
    fun track(dispatcher: CoroutineDispatcher, name: String, longRunThreshold: Duration): CoroutineDispatcher {
        require(dispatcher !== Dispatchers.Unconfined) { "Dispatchers.Unconfined runs tasks in place; there is no dispatch to time" }
        val recorder = DispatcherRecorder(name = name, longRunThreshold = longRunThreshold)
        dispatchers.updateAndGet { current ->
            require(current.none { it.name == name }) { "a dispatcher is already tracked as '$name'" }
            current + recorder
        }
        return TrackedDispatcher(dispatcher, recorder)
    }

    /**
     * Returns [flow] recording each collection of it under [name]: when it starts and how it
     * ends, how many run at once, and its recent values as text. Flows tracked under one name
     * share one record, and a record is never dropped, so name a flow for its purpose rather than
     * for the instance that exposes it. A `StateFlow` or `SharedFlow` comes back as a plain `Flow`, so track it
     * where it is collected rather than where it is exposed.
     */
    fun <T> track(flow: Flow<T>, name: String): Flow<T> {
        val recorder = flows.updateAndGet { current ->
            if (name in current) current else current + (name to FlowRecorder(name))
        }.getValue(name)
        return trackedFlow(flow, recorder)
    }

    override fun onActivate() {
        sighting = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                walkLock.withLock { walker.sight(liveRoots().values) }
                delay(SIGHTING_INTERVAL)
            }
        }
    }

    override fun onDeactivate() {
        sighting?.cancel()
        sighting = null
    }

    internal suspend fun coroutineTree(): CoroutineTree = walkLock.withLock {
        walker.walk(liveRoots(), capturedAtEpochMillis = nowEpochMillis())
    }

    internal fun dispatcherStats(): DispatcherStatsReport = DispatcherStatsReport(
        dispatchers = dispatchers.load().map(DispatcherRecorder::snapshot),
        untracked = untrackedDispatchers(liveRoots().values, nodeLimit = MAX_TREE_NODES),
        capturedAtEpochMillis = nowEpochMillis(),
    )

    private fun liveRoots(): Map<String, Job> = roots.updateAndGet { current -> current.filterValues { it.get() != null } }
        .mapNotNull { (name, reference) -> reference.get()?.let { name to it } }
        .toMap()

    /** What can be told about the coroutine the last tree gave [id]; see [GetCoroutineDetail]. */
    internal suspend fun coroutineDetail(id: String): CoroutineDetail {
        val job = walkLock.withLock { walker.find(id) }
            ?: return CoroutineDetail(id = id, found = false, debugState = null, suspensionStack = emptyList(), creationStack = emptyList(), stackUnavailableReason = null)
        return describeCoroutine(id, job)
    }

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetCoroutineTree ->
            reply(coroutineTree())
        }
        onRequest { _: GetDispatcherStats ->
            reply(dispatcherStats())
        }
        onRequest { _: GetTrackedFlows ->
            reply(TrackedFlowReport(flows.load().values.map(FlowRecorder::snapshot).sortedBy(TrackedFlowInfo::name), capturedAtEpochMillis = nowEpochMillis()))
        }
        onRequest { _: DumpCoroutines -> reply(dumpCoroutines()) }
        onRequest { request: GetCoroutineDetail -> reply(coroutineDetail(request.id)) }
        onRequest { _: ClearLongRuns -> reply(ClearedLongRuns(dispatchers.load().sumOf(DispatcherRecorder::clearLongRuns))) }
    }
}

/** Enough to show every coroutine of an ordinary app, few enough that a runaway leak cannot flood the connection. */
private const val MAX_TREE_NODES = 5_000

/** Often enough that an age is off by at most this much; rare enough to cost next to nothing. */
private val SIGHTING_INTERVAL = 2.seconds

internal fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalAtomicApi::class)
internal inline fun <T> AtomicReference<T>.updateAndGet(transform: (T) -> T): T {
    while (true) {
        val current = load()
        val next = transform(current)
        if (compareAndSet(current, next)) return next
    }
}
