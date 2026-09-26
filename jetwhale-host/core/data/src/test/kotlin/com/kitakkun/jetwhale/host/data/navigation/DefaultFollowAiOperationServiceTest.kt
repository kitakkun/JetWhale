package com.kitakkun.jetwhale.host.data.navigation

import com.kitakkun.jetwhale.host.data.server.DefaultMcpActivityRepository
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultFollowAiOperationServiceTest {

    private val activityRepository = DefaultMcpActivityRepository()
    private val navigationService = DefaultHostNavigationService()
    private val followEnabled = MutableStateFlow(true)
    private val settingsRepository = mock<DebuggerSettingsRepository>(MockMode.autoUnit) {
        every { followAiOperationEnabledFlow } returns this@DefaultFollowAiOperationServiceTest.followEnabled
    }

    /** The (pluginId, sessionId) pairs that have a live instance; a call to any other plugin fails. */
    private val runningInstances = setOf("plugin-1" to "session-1", "plugin-1" to "session-2", "plugin-2" to "session-1", "host-plugin" to HostSession.ID)
    private val runningPlugin = object : JetWhaleHostPlugin() {}
    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getPluginInstanceForSession(any(), any()) } calls { args ->
            runningPlugin.takeIf { (args.args[0] as String to args.args[1] as String) in runningInstances }
        }
        every { getLoadedPluginInstances() } returns runningInstances.map { (pluginId, sessionId) -> LoadedPluginInstance(pluginId, sessionId, runningPlugin) }
    }

    private val service = DefaultFollowAiOperationService(
        mcpActivityRepository = activityRepository,
        debuggerSettingsRepository = settingsRepository,
        hostNavigationService = navigationService,
        pluginInstanceService = pluginInstanceService,
    )

    @Test
    fun `a plugin tool call points the window at that plugin`() = runBlocking {
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")

        assertEquals(HostNavigationRequest.Plugin("plugin-1", "session-1", followsAgent = true), awaitRequest())
        following.cancel()
    }

    private fun CoroutineScope.startFollowing(): Job = launch { service.followAiOperations() }

    /** Starts a tool call the way `McpToolRegistrar` does when it wraps a handler. */
    private fun startCall(toolName: String, pluginId: String?, sessionId: String?) {
        activityRepository.toolInvocationStarted(
            toolName = toolName,
            pluginId = pluginId,
            sessionId = sessionId,
            arguments = emptyMap(),
        )
    }

    private suspend fun awaitRequest(): HostNavigationRequest? = withTimeout(5_000) { navigationService.requests.first() }

    @Test
    fun `nothing is followed while the mode is off`() = runBlocking {
        followEnabled.value = false
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    /**
     * A navigation that never comes can only be observed by waiting for one, so this waits long
     * enough that a request the service was going to send would have arrived by now.
     */
    private suspend fun awaitNoRequest(): HostNavigationRequest? = withTimeoutOrNull(300) { navigationService.requests.first() }

    @Test
    fun `turning the mode back on follows the next call`() = runBlocking {
        followEnabled.value = false
        val following = startFollowing()
        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")
        assertNull(awaitNoRequest())

        followEnabled.value = true
        startCall("jetwhale.click", pluginId = "plugin-2", sessionId = "session-1")

        assertEquals(HostNavigationRequest.Plugin("plugin-2", "session-1", followsAgent = true), awaitRequest())
        following.cancel()
    }

    @Test
    fun `a call that names no plugin is not followed`() = runBlocking {
        val following = startFollowing()

        startCall("jetwhale.host_status", pluginId = null, sessionId = "session-1")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `the plugin already on screen is not navigated to again`() = runBlocking {
        navigationService.updateDestination(
            HostDestination(
                kind = HostDestinationKind.PLUGIN,
                pluginId = "plugin-1",
                sessionId = "session-1",
            ),
        )
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `the same plugin in another session is still followed`() = runBlocking {
        navigationService.updateDestination(
            HostDestination(
                kind = HostDestinationKind.PLUGIN,
                pluginId = "plugin-1",
                sessionId = "session-1",
            ),
        )
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-2")

        assertEquals(HostNavigationRequest.Plugin("plugin-1", "session-2", followsAgent = true), awaitRequest())
        following.cancel()
    }

    @Test
    fun `a plugin popped out into its own window is left where it is`() = runBlocking {
        navigationService.updateDestination(
            HostDestination(
                kind = HostDestinationKind.HOME,
                poppedOutPlugins = listOf(PoppedOutPlugin("plugin-1", "session-1")),
            ),
        )
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `a call to a plugin with nothing running is not followed`() = runBlocking {
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-off", sessionId = "session-1")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `a call to a plugin that runs only in another session is not followed into this one`() = runBlocking {
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-2", sessionId = "session-2")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `a call that names no session is followed in the selected app when the plugin runs there`() = runBlocking {
        navigationService.updateDestination(HostDestination(kind = HostDestinationKind.HOME))
        navigationService.updateSelection(selectedSessionId = "session-1", selectedPluginId = null)
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-2", sessionId = null)

        assertEquals(HostNavigationRequest.Plugin("plugin-2", "session-1", followsAgent = true), awaitRequest())
        following.cancel()
    }

    @Test
    fun `a call that names no session is not followed when the plugin runs only outside the selected app`() = runBlocking {
        navigationService.updateDestination(HostDestination(kind = HostDestinationKind.HOME))
        navigationService.updateSelection(selectedSessionId = "session-2", selectedPluginId = null)
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-2", sessionId = null)

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `a call that names no session follows a plugin that needs no app into the host session`() = runBlocking {
        navigationService.updateDestination(HostDestination(kind = HostDestinationKind.HOME))
        navigationService.updateSelection(selectedSessionId = "session-1", selectedPluginId = null)
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "host-plugin", sessionId = null)

        assertEquals(HostNavigationRequest.Plugin("host-plugin", HostSession.ID, followsAgent = true), awaitRequest())
        following.cancel()
    }
}
