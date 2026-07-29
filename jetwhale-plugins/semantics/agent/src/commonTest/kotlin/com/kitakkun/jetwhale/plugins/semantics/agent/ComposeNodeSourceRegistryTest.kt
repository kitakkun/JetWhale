package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeNodeSourceRegistryTest {
    @BeforeTest
    @AfterTest
    fun resetRegistry() {
        ComposeNodeSourceRegistry.clear()
    }

    @Test
    fun `registers a source and unregisters it when its registration is closed`() {
        val registration = ComposeNodeSourceRegistry.register(FakeSource("root-1"))

        assertEquals(listOf("root-1"), ComposeNodeSourceRegistry.sources.map { it.sourceId })

        registration.close()

        assertEquals(emptyList(), ComposeNodeSourceRegistry.sources.map { it.sourceId })
    }

    @Test
    fun `keeps registration order so the newest root is last`() {
        ComposeNodeSourceRegistry.register(FakeSource("root-1"))
        ComposeNodeSourceRegistry.register(FakeSource("root-2"))

        assertEquals(listOf("root-1", "root-2"), ComposeNodeSourceRegistry.sources.map { it.sourceId })
    }

    @Test
    fun `registering the same root twice reports it once`() {
        ComposeNodeSourceRegistry.register(FakeSource("root-1"))
        ComposeNodeSourceRegistry.register(FakeSource("root-1"))

        assertEquals(1, ComposeNodeSourceRegistry.sources.size)
    }

    @Test
    fun `a root stays registered until the last claim on it is released`() {
        // The Application-level probe and the in-composition one can both claim the same root; the
        // first to be torn down must not take it away from the other.
        val first = ComposeNodeSourceRegistry.register(FakeSource("root-1"))
        val second = ComposeNodeSourceRegistry.register(FakeSource("root-1"))

        first.close()
        assertEquals(1, ComposeNodeSourceRegistry.sources.size)

        second.close()
        assertEquals(0, ComposeNodeSourceRegistry.sources.size)
    }

    @Test
    fun `closing a registration twice does not release someone else's claim`() {
        val first = ComposeNodeSourceRegistry.register(FakeSource("root-1"))
        ComposeNodeSourceRegistry.register(FakeSource("root-1"))

        first.close()
        first.close()

        assertEquals(1, ComposeNodeSourceRegistry.sources.size)
    }

    private class FakeSource(override val sourceId: String) : ComposeNodeSource {
        override suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot? = null
        override suspend fun performAction(request: PerformNodeAction): NodeActionResult = NodeActionResult(performed = false)
    }
}
