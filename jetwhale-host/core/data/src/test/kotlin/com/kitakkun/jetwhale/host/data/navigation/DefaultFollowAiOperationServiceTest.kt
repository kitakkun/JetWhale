package com.kitakkun.jetwhale.host.data.navigation

import com.kitakkun.jetwhale.host.data.server.DefaultMcpActivityRepository
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
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

    private val service = DefaultFollowAiOperationService(
        mcpActivityRepository = activityRepository,
        debuggerSettingsRepository = settingsRepository,
        hostNavigationService = navigationService,
    )

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

    /**
     * A navigation that never comes can only be observed by waiting for one, so this waits long
     * enough that a request the service was going to send would have arrived by now.
     */
    private suspend fun awaitNoRequest(): HostNavigationRequest? = withTimeoutOrNull(300) { navigationService.requests.first() }

    @Test
    fun `a plugin tool call points the window at that plugin`() = runBlocking {
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")

        assertEquals(HostNavigationRequest.Plugin("plugin-1", "session-1"), awaitRequest())
        following.cancel()
    }

    @Test
    fun `nothing is followed while the mode is off`() = runBlocking {
        followEnabled.value = false
        val following = startFollowing()

        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")

        assertNull(awaitNoRequest())
        following.cancel()
    }

    @Test
    fun `turning the mode back on follows the next call`() = runBlocking {
        followEnabled.value = false
        val following = startFollowing()
        startCall("jetwhale.click", pluginId = "plugin-1", sessionId = "session-1")
        assertNull(awaitNoRequest())

        followEnabled.value = true
        startCall("jetwhale.click", pluginId = "plugin-2", sessionId = "session-1")

        assertEquals(HostNavigationRequest.Plugin("plugin-2", "session-1"), awaitRequest())
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

        assertEquals(HostNavigationRequest.Plugin("plugin-1", "session-2"), awaitRequest())
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
}
