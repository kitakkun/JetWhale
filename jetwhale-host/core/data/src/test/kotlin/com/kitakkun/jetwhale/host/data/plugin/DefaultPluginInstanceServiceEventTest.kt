package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DefaultPluginInstanceServiceEventTest {
    private val pluginId = "com.example.plugin"

    private val storage = mock<JetWhalePluginStorage>()
    private val storageService = mock<PluginStorageService> {
        every { storageFor(any()) } returns storage
    }

    private val service = DefaultPluginInstanceService(
        pluginFactoryRepository = SinglePluginFactoryRepository(
            LoadedHostPlugin(
                jarPath = "/plugins/plugin.jar",
                manifest = JetWhaleHostPluginManifest(
                    pluginId = pluginId,
                    pluginName = "Test",
                    version = "1.0.0",
                    factoryClass = "com.example.TestFactory",
                    requiresAgent = false,
                ),
                factory = object : JetWhaleHostPluginFactory {
                    override fun createPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
                },
            ),
        ),
        frameSender = mock<HostPluginFrameSender>(),
        pluginStorageService = storageService,
    )

    @Test
    fun `the server stopping reports every app session's instance as disposed and none of the host's`() = runBlocking {
        val appSessions = List(APP_SESSIONS_IN_ONE_STOP) { "session-$it" }.toSet()
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, (appSessions + HostSession.ID).associateWith { null })
        val disposed = async(start = CoroutineStart.UNDISPATCHED) {
            service.pluginInstanceEventFlow.filterIsInstance<PluginInstanceEvent.Disposed>().take(appSessions.size).toList()
        }

        service.clearAppSessionPluginInstances()

        val reported = withTimeout(5.seconds) { disposed.await() }
        assertEquals(appSessions.map { PluginInstanceEvent.Disposed(pluginId, it) }.toSet(), reported.toSet())
    }
}

/** More instances than a small event buffer holds, so a stop that drops events would show it. */
private const val APP_SESSIONS_IN_ONE_STOP = 100
