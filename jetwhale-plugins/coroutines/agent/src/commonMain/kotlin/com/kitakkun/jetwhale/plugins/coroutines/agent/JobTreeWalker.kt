package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Walks the coroutines below the registered jobs through `Job.children`. It remembers each job
 * it has seen so that ids stay stable and an observed age can be told; a job no walk finds any
 * more is forgotten. Between walks it holds the jobs weakly, so a tree the app has let go of can
 * be collected. Not thread-safe: callers walk one at a time. [registered] alone may be called from
 * any thread.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class JobTreeWalker(private val nodeLimit: Int) {
    private var seen: List<Pair<WeakReference<Job>, Sighting>> = emptyList()
    private var nextId = 1L

    // Registered jobs no walk has reached yet, with when they were registered: a root's age then
    // counts from its registration rather than from the first time someone looked.
    private val registrations = AtomicReference(emptyList<Pair<WeakReference<Job>, TimeMark>>())

    /** Remembers that [job] was registered now, so its age counts from here. */
    fun registered(job: Job) {
        val registeredAt = TimeSource.Monotonic.markNow()
        registrations.updateAndGet { it + (WeakReference(job) to registeredAt) }
    }

    fun walk(roots: Map<String, Job>, capturedAtEpochMillis: Long): CoroutineTree {
        val walk = Walk(previous = previousSightings())
        val nodes = mutableListOf<CoroutineNode>()
        for ((name, job) in roots) {
            if (walk.count >= nodeLimit) {
                walk.truncated = true
                break
            }
            nodes += walk.node(job, rootName = name)
        }
        remember(walk.sightings)
        return CoroutineTree(roots = nodes, coroutineCount = walk.count, truncated = walk.truncated, capturedAtEpochMillis = capturedAtEpochMillis)
    }

    /**
     * Notes every job below [roots] that no walk has seen yet, without building a tree, so that a
     * coroutine's age counts from about when it appeared rather than from when the tree was first
     * asked for.
     */
    fun sight(roots: Collection<Job>) {
        val previous = previousSightings()
        val sightings = mutableMapOf<Job, Sighting>()
        val pending = ArrayDeque(roots.take(nodeLimit))
        while (pending.isNotEmpty() && sightings.size < nodeLimit) {
            val job = pending.removeFirst()
            if (job in sightings) continue
            sightings[job] = previous[job] ?: newSighting(job)
            for (child in job.children) {
                if (sightings.size + pending.size >= nodeLimit) break
                pending.addLast(child)
            }
        }
        remember(sightings)
    }

    /** The job the last walk gave [id], while it is still reachable. */
    fun find(id: String): Job? = seen.firstNotNullOfOrNull { (reference, sighting) -> if (sighting.id == id) reference.get() else null }

    private fun previousSightings(): Map<Job, Sighting> = seen.mapNotNull { (reference, sighting) -> reference.get()?.let { it to sighting } }.toMap()

    private fun remember(sightings: Map<Job, Sighting>) {
        seen = sightings.map { (job, sighting) -> WeakReference(job) to sighting }
        registrations.updateAndGet { pending -> pending.filter { (reference, _) -> reference.get()?.let { it !in sightings } == true } }
    }

    private fun newSighting(job: Job): Sighting {
        val registeredAt = registrations.load().firstNotNullOfOrNull { (reference, at) -> at.takeIf { reference.get() === job } }
        return Sighting(id = "c${nextId++}", firstSeen = registeredAt ?: TimeSource.Monotonic.markNow())
    }

    private inner class Walk(private val previous: Map<Job, Sighting>) {
        val sightings = mutableMapOf<Job, Sighting>()
        var count = 0
        var truncated = false

        fun node(job: Job, rootName: String?): CoroutineNode {
            count++
            val sighting = sightings.getOrPut(job) { previous[job] ?: newSighting(job) }
            // A coroutine started by launch or async is its own CoroutineScope, which is how its
            // context — name and dispatcher — is reachable from the Job.
            val context = (job as? CoroutineScope)?.coroutineContext
            val children = mutableListOf<CoroutineNode>()
            for (child in job.children) {
                if (count >= nodeLimit) {
                    truncated = true
                    break
                }
                children += node(child, rootName = null)
            }
            return CoroutineNode(
                id = sighting.id,
                name = rootName ?: context?.get(CoroutineName)?.name,
                state = job.coroutineState(),
                observedMillis = sighting.firstSeen.elapsedNow().inWholeMilliseconds,
                dispatcher = context?.get(ContinuationInterceptor)?.toString(),
                description = job.toString(),
                children = children,
            )
        }
    }

    private class Sighting(val id: String, val firstSeen: TimeMark)
}

internal fun Job.coroutineState(): CoroutineState = when {
    isCompleted && isCancelled -> CoroutineState.Cancelled
    isCompleted -> CoroutineState.Completed
    isCancelled -> CoroutineState.Cancelling
    isActive -> CoroutineState.Active
    else -> CoroutineState.New
}
