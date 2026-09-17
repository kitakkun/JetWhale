package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.HostViewState
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostNavigationCommandTest {

    private val session = DebugSession(
        id = "session-1",
        name = "Pixel",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(JetWhalePluginInfo("com.example.agent", "1.0.0")),
    )

    private val currentView = MutableStateFlow<HostViewState?>(null)
    private val enabledPluginIds = MutableStateFlow(setOf("com.example.agent", "com.example.hostonly"))

    private val hostNavigationService = mock<HostNavigationService>(MockMode.autoUnit) {
        every { this@mock.currentView } returns this@HostNavigationCommandTest.currentView
    }
    private val debugSessionRepository = mock<DebugSessionRepository> {
        every { debugSessionsFlow } returns flowOf(persistentListOf(session))
    }
    private val pluginFactoryRepository = mock<PluginFactoryRepository> {
        every { loadedPlugins } returns mapOf(
            "com.example.agent" to loadedPlugin("com.example.agent"),
            "com.example.hostonly" to loadedPlugin("com.example.hostonly"),
            "com.example.disabled" to loadedPlugin("com.example.disabled"),
        )
    }
    private val enabledPluginsRepository = mock<EnabledPluginsRepository> {
        every { enabledPluginIdsFlow } returns enabledPluginIds
    }
    private val reconciliationService = mock<PluginSessionReconciliationService> {
        every { requiresAgent("com.example.agent") } returns true
        every { requiresAgent("com.example.hostonly") } returns false
    }

    private val command = HostNavigationCommand(
        hostNavigationService,
        debugSessionRepository,
        pluginFactoryRepository,
        enabledPluginsRepository,
        reconciliationService,
    )

    @Test
    fun `navigate reports the destination the host switched to`() = runBlocking {
        currentView.value = viewState(
            HostDestination(kind = HostDestinationKind.SETTINGS, settingsSection = HostSettingsSection.SERVER),
        )

        val result = command
            .executeForText(arguments("destination" to JsonPrimitive("SETTINGS"), "settingsSection" to JsonPrimitive("SERVER")))
            .decode()

        assertTrue(result.applied)
        assertEquals("SETTINGS", result.destination)
        assertEquals("SERVER", result.settingsSection)
    }

    @Test
    fun `navigate does not confirm a settings section other than the one requested`() = runBlocking {
        currentView.value = viewState(
            HostDestination(kind = HostDestinationKind.SETTINGS, settingsSection = HostSettingsSection.GENERAL),
        )

        val result = command
            .executeForText(arguments("destination" to JsonPrimitive("SETTINGS"), "settingsSection" to JsonPrimitive("PLUGINS")))
            .decode()

        assertFalse(result.applied)
    }

    @Test
    fun `navigate reports not-applied when the host window never confirms`() = runBlocking {
        val result = command.executeForText(arguments("destination" to JsonPrimitive("INFO"))).decode()

        assertFalse(result.applied)
        assertContains(result.reason.orEmpty(), "did not report")
    }

    @Test
    fun `navigate reports that the target plugin is popped out`() = runBlocking {
        currentView.value = viewState(
            HostDestination(
                kind = HostDestinationKind.PLUGIN,
                pluginId = "com.example.agent",
                sessionId = "session-1",
                poppedOutPlugins = listOf(PoppedOutPlugin("com.example.agent", "session-1")),
            ),
        )

        val result = command
            .executeForText(arguments("destination" to JsonPrimitive("PLUGIN"), "pluginId" to JsonPrimitive("com.example.agent")))
            .decode()

        assertTrue(result.applied)
        assertTrue(result.poppedOut)
    }

    @Test
    fun `navigate requires a pluginId when the destination is PLUGIN`(): Unit = runBlocking {
        val error = assertFailsWithArgumentException { command.executeForText(arguments("destination" to JsonPrimitive("PLUGIN"))) }
        assertContains(error, "pluginId is required")
    }

    @Test
    fun `navigate rejects a pluginId that is not installed`(): Unit = runBlocking {
        val error = assertFailsWithArgumentException {
            command.executeForText(arguments("destination" to JsonPrimitive("PLUGIN"), "pluginId" to JsonPrimitive("com.example.missing")))
        }
        assertContains(error, "is not installed")
    }

    @Test
    fun `navigate rejects a plugin that is installed but disabled`(): Unit = runBlocking {
        val error = assertFailsWithArgumentException {
            command.executeForText(arguments("destination" to JsonPrimitive("PLUGIN"), "pluginId" to JsonPrimitive("com.example.disabled")))
        }
        assertContains(error, "disabled")
    }

    @Test
    fun `navigate rejects a session that does not exist`(): Unit = runBlocking {
        val error = assertFailsWithArgumentException {
            command.executeForText(
                arguments(
                    "destination" to JsonPrimitive("PLUGIN"),
                    "pluginId" to JsonPrimitive("com.example.agent"),
                    "sessionId" to JsonPrimitive("session-missing"),
                ),
            )
        }
        assertContains(error, "no session")
    }

    @Test
    fun `navigate rejects an agent-backed plugin the session does not advertise`(): Unit = runBlocking {
        every { reconciliationService.requiresAgent("com.example.hostonly") } returns true

        val error = assertFailsWithArgumentException {
            command.executeForText(
                arguments(
                    "destination" to JsonPrimitive("PLUGIN"),
                    "pluginId" to JsonPrimitive("com.example.hostonly"),
                    "sessionId" to JsonPrimitive("session-1"),
                ),
            )
        }
        assertContains(error, "does not have")
    }
}

private inline fun assertFailsWithArgumentException(block: () -> Unit): String = try {
    block()
    error("expected a JetWhaleMcpArgumentException")
} catch (e: JetWhaleMcpArgumentException) {
    e.message.orEmpty()
}

private fun viewState(destination: HostDestination) = HostViewState(
    destination = destination,
    selectedSessionId = "session-1",
    selectedPluginId = destination.pluginId,
)

private fun loadedPlugin(pluginId: String) = LoadedHostPlugin(
    manifest = JetWhaleHostPluginManifest(
        pluginId = pluginId,
        pluginName = pluginId,
        version = "1.0.0",
        factoryClass = "$pluginId.Factory",
    ),
    factory = object : JetWhaleHostPluginFactory {
        override fun createPlugin(): JetWhaleHostPlugin = throw UnsupportedOperationException()
    },
)

private fun arguments(vararg entries: Pair<String, JsonPrimitive>) = JetWhaleMcpArguments(JsonObject(entries.toMap()))

private fun String.decode() = Json.decodeFromString<NavigateResult>(this)
