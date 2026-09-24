package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFilterOptionsTest {
    @Test
    fun `a disconnected session is listed after the connected ones and marked`() {
        val options = sessionFilterOptions(
            listOf(session("gone", appName = "DroidKaigi", isActive = false), session("live", appName = "Demo", isActive = true)),
            disconnectedLabel = "disconnected",
        )

        assertEquals(listOf("live", "gone"), options.map(McpFilterOption::id))
        assertEquals(listOf("Pixel · Demo", "Pixel · DroidKaigi · disconnected"), options.map(McpFilterOption::label))
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
