package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PendingRequestStoreTest {
    @Test
    fun `newCorrelationId returns distinct 32-char hex ids`() {
        val store = PendingRequestStore()
        val ids = List(1000) { store.newCorrelationId() }
        ids.forEach { assertEquals(32, it.length) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `complete resolves the matching deferred and reports success`() = runBlocking {
        val store = PendingRequestStore()
        val deferred = CompletableDeferred<PluginFrame.Reply>()
        store.register("corr-1", deferred)

        val reply = PluginFrame.Reply.Success(pluginId = "p", inReplyTo = "corr-1", payload = "ok")
        assertTrue(store.complete(reply))
        assertEquals(reply, deferred.await())
    }

    @Test
    fun `complete on unknown correlation id reports false`() = runBlocking {
        val store = PendingRequestStore()
        val reply = PluginFrame.Reply.Success(pluginId = "p", inReplyTo = "missing", payload = "ok")
        assertFalse(store.complete(reply))
    }

    @Test
    fun `removed request is no longer completable`() = runBlocking {
        val store = PendingRequestStore()
        val deferred = CompletableDeferred<PluginFrame.Reply>()
        store.register("corr-1", deferred)
        store.remove("corr-1")

        val reply = PluginFrame.Reply.Success(pluginId = "p", inReplyTo = "corr-1", payload = "ok")
        assertFalse(store.complete(reply))
        assertFalse(deferred.isCompleted)
    }

    @Test
    fun `failAll fails every pending request and clears the store`() = runBlocking {
        val store = PendingRequestStore()
        val a = CompletableDeferred<PluginFrame.Reply>()
        val b = CompletableDeferred<PluginFrame.Reply>()
        store.register("a", a)
        store.register("b", b)

        store.failAll()

        assertFailsWith<JetWhaleConnectionClosedException> { a.await() }
        assertFailsWith<JetWhaleConnectionClosedException> { b.await() }
        // After failAll the store is empty, so a late reply matches nothing.
        assertFalse(store.complete(PluginFrame.Reply.Success(pluginId = "p", inReplyTo = "a", payload = "ok")))
    }
}
