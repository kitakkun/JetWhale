package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFilterOptionsTest {
    @Test
    fun `a disconnected session is listed after the connected ones and marked`() {
        val options = sessionFilterOptions(
            listOf(session("gone", appName = "Other", isActive = false), session("live", appName = "Demo", isActive = true)),
            hostLabel = "Tools",
            disconnectedLabel = "disconnected",
        )

        assertEquals(listOf(HostSession.ID, "live", "gone"), options.map(McpFilterOption::id))
        assertEquals(listOf("Tools", "Pixel · Demo", "Pixel · Other · disconnected"), options.map(McpFilterOption::label))
    }

    @Test
    fun `the host session is offered even with no app connected`() {
        val options = sessionFilterOptions(emptyList(), hostLabel = "Tools", disconnectedLabel = "disconnected")

        assertEquals(listOf(HostSession.ID), options.map(McpFilterOption::id))
    }

    private fun session(id: String, appName: String, isActive: Boolean) = DebugSession(
        id = id,
        name = appName,
        isActive = isActive,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
        appName = appName,
        deviceId = "device",
        deviceName = "Pixel",
    )
}
