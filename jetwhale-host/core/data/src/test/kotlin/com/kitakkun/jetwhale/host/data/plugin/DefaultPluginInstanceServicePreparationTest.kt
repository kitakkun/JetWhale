package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest.AgentVersionRange
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class DefaultPluginInstanceServicePreparationTest {
    /** Each frame the instances send, with the session it is sent to. */
    private val sentFrames = Channel<Pair<String, PluginFrame>>(Channel.UNLIMITED)

    @Test
    fun `an instance of a plugin that needs an agent prepares only once its preparation is started`() = runBlocking {
        val service = serviceFor(requiresAgent = true)
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, mapOf(SESSION_ID to "1.0.0"))

        assertNull(receiveSentFrameOrNull())

        service.startPluginInstancePreparation(PLUGIN_ID, SESSION_ID)
        assertIs<PluginFrame.Request>(withTimeout(5.seconds) { sentFrames.receive() }.second)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `an instance of a plugin that needs no agent prepares as soon as it is created`() = runBlocking {
        val service = serviceFor(requiresAgent = false)
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, mapOf(HostSession.ID to null))

        assertIs<PluginFrame.Request>(withTimeout(5.seconds) { sentFrames.receive() }.second)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `the instance of each version prepares only once the preparation of its own session is started`() = runBlocking {
        val factoryRepository = JarPluginFactoryRepository()
        factoryRepository.load(requestingPlugin(version = "1.2.0", requiresAgent = true, agentVersionRange = AgentVersionRange(max = "1.2.9")))
        factoryRepository.load(requestingPlugin(version = "1.3.0", requiresAgent = true, agentVersionRange = AgentVersionRange(min = "1.3.0")))
        val service = serviceWith(factoryRepository)
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, mapOf("old-app" to "1.2.0", "new-app" to "1.3.0"))

        assertNull(receiveSentFrameOrNull())

        service.startPluginInstancePreparation(PLUGIN_ID, "old-app")
        assertEquals("old-app", withTimeout(5.seconds) { sentFrames.receive() }.first)
        assertNull(receiveSentFrameOrNull())

        service.startPluginInstancePreparation(PLUGIN_ID, "new-app")
        assertEquals("new-app", withTimeout(5.seconds) { sentFrames.receive() }.first)
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    /**
     * A preparation that has not started can only be observed by waiting for its request, so this
     * waits long enough that a preparation launched with the instance would have sent it by now.
     */
    private suspend fun receiveSentFrameOrNull(): Pair<String, PluginFrame>? = withTimeoutOrNull(300) { sentFrames.receive() }

    private fun serviceFor(requiresAgent: Boolean) = serviceWith(
        SinglePluginFactoryRepository(requestingPlugin(version = "1.0.0", requiresAgent = requiresAgent, agentVersionRange = null)),
    )

    private fun serviceWith(pluginFactoryRepository: PluginFactoryRepository) = DefaultPluginInstanceService(
        pluginFactoryRepository = pluginFactoryRepository,
        frameSender = object : HostPluginFrameSender {
            override suspend fun sendFrame(sessionId: String, frame: PluginFrame) {
                sentFrames.send(sessionId to frame)
            }
        },
        pluginStorageService = mock<PluginStorageService> {
            every { storageFor(any()) } returns mock()
        },
    )

    private fun requestingPlugin(version: String, requiresAgent: Boolean, agentVersionRange: AgentVersionRange?) = LoadedHostPlugin(
        jarPath = "/plugins/plugin-$version.jar",
        manifest = JetWhaleHostPluginManifest(
            pluginId = PLUGIN_ID,
            pluginName = "Test",
            version = version,
            factoryClass = "com.example.TestFactory",
            requiresAgent = requiresAgent,
            agentVersionRange = agentVersionRange,
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = RequestingOnPreparePlugin()
        },
    )

    private class RequestingOnPreparePlugin : JetWhaleMessagingHostPlugin() {
        override suspend fun onPrepare() {
            messenger.requestRaw(messageType = "com.example.plugin/prepare", payload = "{}", timeout = null)
        }
    }

    private companion object {
        const val PLUGIN_ID = "com.example.plugin"
        const val SESSION_ID = "session-1"
    }
}
