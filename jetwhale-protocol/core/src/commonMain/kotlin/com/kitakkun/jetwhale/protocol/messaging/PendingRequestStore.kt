package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * The correlation store for one [JetWhalePluginPeer]: it assigns correlation ids and keeps the map
 * of in-flight outbound requests to the deferreds awaiting their replies. All access is guarded by a
 * single mutex so completing a reply cannot race a concurrent register/remove.
 */
internal class PendingRequestStore {
    private val mutex = Mutex()
    private val pendingReplies = mutableMapOf<String, CompletableDeferred<PluginFrame.Reply>>()

    fun newCorrelationId(): String = Random.nextLong().toULong().toString(16).padStart(16, '0') +
        Random.nextLong().toULong().toString(16).padStart(16, '0')

    suspend fun register(correlationId: String, deferred: CompletableDeferred<PluginFrame.Reply>) {
        mutex.withLock { pendingReplies[correlationId] = deferred }
    }

    suspend fun remove(correlationId: String) {
        mutex.withLock { pendingReplies.remove(correlationId) }
    }

    /**
     * Completes the pending request that [reply] answers. Returns `false` when no request matched —
     * a late reply after timeout/close, or a correlation bug on the other side — so the caller can log.
     */
    suspend fun complete(reply: PluginFrame.Reply): Boolean {
        val deferred = mutex.withLock { pendingReplies.remove(reply.inReplyTo) } ?: return false
        deferred.complete(reply)
        return true
    }

    /** Fails every pending request with [JetWhaleConnectionClosedException] and clears the store. */
    suspend fun failAll() {
        mutex.withLock {
            pendingReplies.values.forEach { it.completeExceptionally(JetWhaleConnectionClosedException()) }
            pendingReplies.clear()
        }
    }
}
