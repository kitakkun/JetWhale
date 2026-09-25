package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.UntrackedDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.coroutines.ContinuationInterceptor

/**
 * The dispatchers the coroutines below [roots] run on, other than tracked ones, with how many
 * coroutines are on each, by state; most first. A coroutine below two overlapping registered
 * scopes is counted once. The walk stops after [nodeLimit] jobs, as the tree does.
 */
internal fun untrackedDispatchers(roots: Collection<Job>, nodeLimit: Int): List<UntrackedDispatcher> {
    val visited = mutableSetOf<Job>()
    val statesByDispatcher = mutableMapOf<String, MutableList<CoroutineState>>()
    val pending = ArrayDeque(roots)
    while (pending.isNotEmpty() && visited.size < nodeLimit) {
        val job = pending.removeFirst()
        if (!visited.add(job)) continue
        // Children are queued only up to the limit, so a scope with a huge fan-out cannot make
        // the queue itself unbounded.
        for (child in job.children) {
            if (visited.size + pending.size >= nodeLimit) break
            pending.addLast(child)
        }
        // Only a coroutine started by launch or async is a CoroutineScope with a dispatcher; a
        // registered scope's own Job is walked through but is not a coroutine.
        val interceptor = (job as? CoroutineScope)?.coroutineContext?.get(ContinuationInterceptor) ?: continue
        if (interceptor is TrackedDispatcher) continue
        statesByDispatcher.getOrPut(interceptor.toString(), ::mutableListOf) += job.coroutineState()
    }
    return statesByDispatcher
        .map { (name, states) -> UntrackedDispatcher(name = name, coroutinesByState = states.groupingBy { it }.eachCount()) }
        .sortedByDescending { it.coroutinesByState.values.sum() }
}
