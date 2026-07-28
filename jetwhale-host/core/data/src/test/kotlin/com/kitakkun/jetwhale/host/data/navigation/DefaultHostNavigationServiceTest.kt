package com.kitakkun.jetwhale.host.data.navigation

import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultHostNavigationServiceTest {

    private val service = DefaultHostNavigationService()

    @Test
    fun `a request emitted before a collector arrives is still delivered`() = runBlocking {
        service.navigate(HostNavigationRequest.Info)

        assertEquals(HostNavigationRequest.Info, service.requests.first())
    }

    @Test
    fun `each request is delivered once, in order`() = runBlocking {
        val received = mutableListOf<HostNavigationRequest>()
        val collector = launch { service.requests.take(2).toList(received) }

        service.navigate(HostNavigationRequest.Info)
        service.navigate(HostNavigationRequest.LogViewer)
        collector.join()

        assertEquals(listOf(HostNavigationRequest.Info, HostNavigationRequest.LogViewer), received)
    }

    @Test
    fun `currentView stays null until a destination is reported`() = runBlocking {
        service.updateSelection(selectedSessionId = "session-1", selectedPluginId = "plugin-1")

        assertNull(service.currentView.value)
    }

    @Test
    fun `currentView combines the destination with the drawer selection`() = runBlocking {
        service.updateSelection(selectedSessionId = "session-1", selectedPluginId = "plugin-1")
        service.updateDestination(
            HostDestination(
                kind = HostDestinationKind.PLUGIN,
                pluginId = "plugin-1",
                sessionId = "session-1",
            ),
        )

        val view = requireNotNull(service.currentView.value)
        assertEquals(HostDestinationKind.PLUGIN, view.destination.kind)
        assertEquals("plugin-1", view.destination.pluginId)
        assertEquals("session-1", view.selectedSessionId)
        assertEquals("plugin-1", view.selectedPluginId)
    }

    @Test
    fun `a later selection update keeps the reported destination`() = runBlocking {
        service.updateDestination(HostDestination(kind = HostDestinationKind.SETTINGS))
        service.updateSelection(selectedSessionId = "session-2", selectedPluginId = null)

        val view = requireNotNull(service.currentView.value)
        assertEquals(HostDestinationKind.SETTINGS, view.destination.kind)
        assertEquals("session-2", view.selectedSessionId)
        assertNull(view.selectedPluginId)
    }
}
