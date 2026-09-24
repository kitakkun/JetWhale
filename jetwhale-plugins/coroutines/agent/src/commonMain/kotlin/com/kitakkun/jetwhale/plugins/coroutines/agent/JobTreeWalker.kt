package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Walks the coroutines below the registered jobs through `Job.children`. It remembers each job
 * it has seen so that ids stay stable and an observed age can be told; a job no walk finds any
 * more is forgotten. Not thread-safe: callers walk one at a time.
 */
internal class JobTreeWalker(private val nodeLimit: Int) {
    private var seen: Map<Job, Sighting> = emptyMap()
    private var nextId = 1L

    fun walk(roots: Map<String, Job>, capturedAtEpochMillis: Long): CoroutineTree {
        val walk = Walk()
        val nodes = mutableListOf<CoroutineNode>()
        for ((name, job) in roots) {
            if (walk.count >= nodeLimit) {
                walk.truncated = true
                break
            }
            nodes += walk.node(job, rootName = name)
        }
        seen = walk.sightings
        return CoroutineTree(roots = nodes, coroutineCount = walk.count, truncated = walk.truncated, capturedAtEpochMillis = capturedAtEpochMillis)
    }

    private inner class Walk {
        val sightings = mutableMapOf<Job, Sighting>()
        var count = 0
        var truncated = false

        fun node(job: Job, rootName: String?): CoroutineNode {
            count++
            val sighting = sightings.getOrPut(job) {
                seen[job] ?: Sighting(id = "c${nextId++}", firstSeen = TimeSource.Monotonic.markNow())
            }
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
