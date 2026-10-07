package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest.AgentVersionRange
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class DefaultPluginJarSwapServiceTest {
    private val jarDirectory: File = Files.createTempDirectory("jetwhale-jar-swap").toFile()

    /** The session each preparation request is sent to, in order. */
    private val preparedSessionIds = Channel<String>(Channel.UNLIMITED)

    private val factoryRepository = JarPluginFactoryRepository()
    private val mutableDebugSessionsFlow = MutableStateFlow<ImmutableList<DebugSession>>(persistentListOf())
    private val debugSessionRepository = mock<DebugSessionRepository> {
        every { debugSessionsFlow } returns mutableDebugSessionsFlow
    }
    private val enabledPluginsRepository = mock<EnabledPluginsRepository> {
        everySuspend { isPluginEnabled(any()) } returns true
    }
    private val instanceService = DefaultPluginInstanceService(
        pluginFactoryRepository = factoryRepository,
        frameSender = object : HostPluginFrameSender {
            override suspend fun sendFrame(sessionId: String, frame: PluginFrame) {
                if (frame is PluginFrame.Request) preparedSessionIds.send(sessionId)
            }
        },
        pluginStorageService = mock<PluginStorageService> {
            every { storageFor(any()) } returns mock()
        },
    )
    private val swapService = DefaultPluginJarSwapService(
        pluginFactoryRepository = factoryRepository,
        pluginInstanceService = instanceService,
        pluginComposeSceneService = mock<PluginComposeSceneService>(MockMode.autoUnit),
        debugSessionRepository = debugSessionRepository,
        enabledPluginsRepository = enabledPluginsRepository,
        reconciliationService = DefaultPluginSessionReconciliationService(
            sessionRepository = debugSessionRepository,
            enabledPluginsRepository = enabledPluginsRepository,
            pluginFactoryRepository = factoryRepository,
            pluginInstanceService = instanceService,
        ),
    )

    @AfterTest
    fun disposeInstancesAndDeleteJars() {
        instanceService.unloadPluginInstancesForPlugin(PLUGIN_ID)
        jarDirectory.deleteRecursively()
    }

    @Test
    fun `a reload prepares the rebuilt instance of a session whose agent has the plugin active`() = runBlocking {
        val jar = writeJarFile("example.jar")
        factoryRepository.load(loadedPluginVersion("1.2.0", jar, AgentVersionRange(max = "1.2.9")))
        connectApp("app", agentVersion = "1.2.0")
        assertEquals("app", receivePreparedSessionId())
        factoryRepository.rebuild(loadedPluginVersion("1.2.0", jar, AgentVersionRange(max = "1.2.9")))

        swapService.reload(jar.absolutePath, expectedSha256 = null)

        assertEquals("app", receivePreparedSessionId())
    }

    @Test
    fun `a reload that makes a version fit a session leaves its instance to reconciliation`() = runBlocking {
        val jar = writeJarFile("example.jar")
        factoryRepository.load(loadedPluginVersion("1.2.0", jar, AgentVersionRange(max = "1.2.9")))
        connectApp("new-app", agentVersion = "1.3.0")
        factoryRepository.rebuild(loadedPluginVersion("1.2.0", jar, agentVersionRange = null))

        swapService.reload(jar.absolutePath, expectedSha256 = null)

        assertNull(instanceService.getPluginInstanceForSession(PLUGIN_ID, "new-app"))
    }

    @Test
    fun `removing a session's version moves the session to another version and prepares it there`() = runBlocking {
        val oldJar = writeJarFile("example-1.2.0.jar")
        factoryRepository.load(loadedPluginVersion("1.2.0", oldJar, agentVersionRange = null))
        connectApp("app", agentVersion = "1.3.0")
        assertEquals("app", receivePreparedSessionId())
        factoryRepository.load(loadedPluginVersion("1.3.0", writeJarFile("example-1.3.0.jar"), agentVersionRange = null))

        swapService.remove(oldJar.absolutePath)

        assertEquals("1.3.0", instanceService.boundPluginVersionsFlow.value.versionOf("app", PLUGIN_ID))
        assertEquals("app", receivePreparedSessionId())
    }

    /**
     * Connects an app as reconciliation and the server do: its instance is created, and prepared once
     * its agent has the plugin active. An app no loaded version fits gets neither.
     */
    private fun connectApp(sessionId: String, agentVersion: String) {
        mutableDebugSessionsFlow.update { current ->
            (
                current + DebugSession(
                    id = sessionId,
                    name = sessionId,
                    isActive = true,
                    transportSecurity = SessionTransportSecurity.LOOPBACK,
                    installedPlugins = persistentListOf(JetWhalePluginInfo(PLUGIN_ID, agentVersion)),
                )
                ).toPersistentList()
        }
        instanceService.initializePluginInstancesForSessionsIfNeeded(PLUGIN_ID, mapOf(sessionId to agentVersion)).forEach { initializedSessionId ->
            instanceService.startPluginInstancePreparation(PLUGIN_ID, initializedSessionId)
        }
    }

    private suspend fun receivePreparedSessionId(): String = withTimeout(5.seconds) { preparedSessionIds.receive() }

    private fun writeJarFile(fileName: String): File = File(jarDirectory, fileName).apply {
        writeBytes(jarBytes(PLUGIN_MANIFEST_PATH, """{"plugins":[]}"""))
    }

    private fun loadedPluginVersion(version: String, jar: File, agentVersionRange: AgentVersionRange?) = LoadedHostPlugin(
        jarPath = jar.absolutePath,
        manifest = JetWhaleHostPluginManifest(
            pluginId = PLUGIN_ID,
            pluginName = "Example",
            version = version,
            factoryClass = "com.example.Factory",
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
    }
}
