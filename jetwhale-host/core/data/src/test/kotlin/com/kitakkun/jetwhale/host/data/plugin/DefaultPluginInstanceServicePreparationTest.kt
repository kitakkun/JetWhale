package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class DefaultPluginInstanceServicePreparationTest {
    private val sentFrames = Channel<PluginFrame>(Channel.UNLIMITED)

    @Test
    fun `an instance of a plugin that needs an agent prepares only once its preparation is started`() = runBlocking {
        val service = serviceFor(requiresAgent = true)
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(SESSION_ID))

        assertNull(receiveSentFrameOrNull())

        service.startPluginInstancePreparation(PLUGIN_ID, SESSION_ID)
        assertIs<PluginFrame.Request>(withTimeout(5.seconds) { sentFrames.receive() })
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    @Test
    fun `an instance of a plugin that needs no agent prepares as soon as it is created`() = runBlocking {
        val service = serviceFor(requiresAgent = false)
        service.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, setOf(HostSession.ID))

        assertIs<PluginFrame.Request>(withTimeout(5.seconds) { sentFrames.receive() })
        service.unloadPluginInstancesForPlugin(PLUGIN_ID)
    }

    /**
     * A preparation that has not started can only be observed by waiting for its request, so this
     * waits long enough that a preparation launched with the instance would have sent it by now.
     */
    private suspend fun receiveSentFrameOrNull(): PluginFrame? = withTimeoutOrNull(300) { sentFrames.receive() }

    private fun serviceFor(requiresAgent: Boolean) = DefaultPluginInstanceService(
        pluginFactoryRepository = SinglePluginFactoryRepository(
            LoadedHostPlugin(
                manifest = JetWhaleHostPluginManifest(
                    pluginId = PLUGIN_ID,
                    pluginName = "Test",
                    version = "1.0.0",
                    factoryClass = "com.example.TestFactory",
                    requiresAgent = requiresAgent,
                ),
                factory = object : JetWhaleHostPluginFactory {
                    override fun createPlugin(): JetWhaleHostPlugin = RequestingOnPreparePlugin()
                },
            ),
        ),
        frameSender = object : HostPluginFrameSender {
            override suspend fun sendFrame(sessionId: String, frame: PluginFrame) {
                sentFrames.send(frame)
            }
        },
        pluginDataStoreRepository = mock<PluginDataStoreRepository> {
            every { storageFor(any()) } returns mock()
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
