package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The store is driven on [Dispatchers.Unconfined], so a coroutine it launches runs as far as its
 * first real suspension point before `launch` returns — a fake that answers straight away leaves
 * the store settled by the time the call under test is over, with no scheduler to pump.
 */
private fun scope(): CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

private fun attribute(id: String, value: ViewAttributeValue): ViewAttribute = ViewAttribute(id = id, label = id, group = "State", value = value, editable = true)

private fun snapshotOf(rootId: String, nodeId: Int, vararg attributes: ViewAttribute): ViewAttributeResponse = ViewAttributeResponse(
    snapshot = ViewAttributeSnapshot(
        rootId = rootId,
        nodeId = nodeId,
        viewClass = "android.widget.TextView",
        attributes = attributes.toList(),
    ),
)

private val alpha = attribute("alpha", ViewAttributeValue.FloatValue(1f))

class ViewAttributeStoreTest {
    @Test
    fun `selecting a node reads its attributes once, and selecting it again reads nothing`() {
        val reads = mutableListOf<GetViewAttributes>()
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request ->
                reads += request
                snapshotOf(request.rootId, request.nodeId, alpha)
            },
            write = { error("not written") },
        )

        store.select(rootId = "window-1", nodeId = -4)
        store.select(rootId = "window-1", nodeId = -4)

        assertEquals(listOf(GetViewAttributes(rootId = "window-1", nodeId = -4)), reads)
        assertEquals(listOf(alpha), store.state.attributes)
        assertNull(store.state.message)
    }

    @Test
    fun `selecting another node reads that one instead`() {
        val reads = mutableListOf<Int>()
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request ->
                reads += request.nodeId
                snapshotOf(request.rootId, request.nodeId, alpha)
            },
            write = { error("not written") },
        )

        store.select(rootId = "window-1", nodeId = -4)
        store.select(rootId = "window-1", nodeId = -9)

        assertEquals(listOf(-4, -9), reads)
        assertEquals(NodeKey(rootId = "window-1", nodeId = -9), store.node)
    }

    @Test
    fun `a node with no attributes to report shows what the app said instead`() {
        val store = ViewAttributeStore(
            scope = scope(),
            read = { ViewAttributeResponse(snapshot = null, message = "this node has no View attributes") },
            write = { error("not written") },
        )

        store.select(rootId = "window-1", nodeId = -4)

        assertNull(store.state.attributes)
        assertEquals("this node has no View attributes", store.state.message)
    }

    @Test
    fun `an app that does not answer the read is reported as such`() {
        val store = ViewAttributeStore(
            scope = scope(),
            read = { throw JetWhaleMessagingException("timed out") },
            write = { error("not written") },
        )

        store.select(rootId = "window-1", nodeId = -4)

        assertNull(store.state.attributes)
        assertEquals("The app did not answer: timed out", store.state.message)
    }

    @Test
    fun `clearing forgets the node it was holding`() {
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { error("not written") },
        )

        store.select(rootId = "window-1", nodeId = -4)
        store.clear()

        assertNull(store.node)
        assertNull(store.state.attributes)
    }

    @Test
    fun `a value the attribute cannot take is reported and never written`() {
        val writes = mutableListOf<SetViewAttribute>()
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { request ->
                writes += request
                ViewAttributeResult(applied = true)
            },
        )
        store.select(rootId = "window-1", nodeId = -4)

        store.commit(alpha, "half")

        assertTrue(writes.isEmpty())
        assertTrue(store.state.writeFailed)
        assertEquals("invalid value for alpha: \"half\" (expected a number)", store.state.writeStatus)
    }

    @Test
    fun `a write shows the value that came back rather than the one that was asked for`() {
        val clamped = attribute("alpha", ViewAttributeValue.FloatValue(1f))
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { ViewAttributeResult(applied = true, attribute = clamped) },
        )
        store.select(rootId = "window-1", nodeId = -4)

        store.commit(alpha, "4")

        assertEquals(listOf(clamped), store.state.attributes)
        assertEquals("alpha: 1.0", store.state.writeStatus)
        assertEquals(false, store.state.writeFailed)
    }

    @Test
    fun `a write the app refuses says so`() {
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { ViewAttributeResult(applied = false, message = "this view ignores alpha") },
        )
        store.select(rootId = "window-1", nodeId = -4)

        store.commit(alpha, "0.5")

        assertTrue(store.state.writeFailed)
        assertEquals("alpha: this view ignores alpha", store.state.writeStatus)
    }

    @Test
    fun `two writes made in quick succession reach the app in the order they were made`() = runBlocking {
        val started = mutableListOf<String>()
        val first = CompletableDeferred<Unit>()
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { request ->
                val text = (request.value as ViewAttributeValue.FloatValue).value.toString()
                started += text
                // The first write is held open, so the second one would overtake it were the two
                // not serialised.
                if (started.size == 1) first.await()
                ViewAttributeResult(applied = true, attribute = attribute("alpha", request.value))
            },
        )
        store.select(rootId = "window-1", nodeId = -4)

        store.commit(alpha, "0.25")
        store.commit(alpha, "0.75")
        assertEquals(listOf("0.25"), started)

        first.complete(Unit)

        assertEquals(listOf("0.25", "0.75"), started)
        assertEquals("alpha: 0.75", store.state.writeStatus)
    }

    @Test
    fun `a write to the node the panel is showing is reflected in it`() = runBlocking {
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { request -> ViewAttributeResult(applied = true, attribute = attribute("alpha", request.value)) },
        )
        store.select(rootId = "window-1", nodeId = -4)

        val result = store.writeAttribute(
            SetViewAttribute(rootId = "window-1", nodeId = -4, attributeId = "alpha", value = ViewAttributeValue.FloatValue(0.5f)),
        )

        assertTrue(result.applied)
        assertEquals(listOf(attribute("alpha", ViewAttributeValue.FloatValue(0.5f))), store.state.attributes)
        assertEquals("alpha: 0.5", store.state.writeStatus)
    }

    @Test
    fun `a write to another node leaves the one the panel is showing alone`() = runBlocking {
        val store = ViewAttributeStore(
            scope = scope(),
            read = { request -> snapshotOf(request.rootId, request.nodeId, alpha) },
            write = { request -> ViewAttributeResult(applied = true, attribute = attribute("alpha", request.value)) },
        )
        store.select(rootId = "window-1", nodeId = -4)

        val result = store.writeAttribute(
            SetViewAttribute(rootId = "window-1", nodeId = -9, attributeId = "alpha", value = ViewAttributeValue.FloatValue(0.5f)),
        )

        assertTrue(result.applied)
        assertEquals(listOf(alpha), store.state.attributes)
        assertNull(store.state.writeStatus)
    }
}
