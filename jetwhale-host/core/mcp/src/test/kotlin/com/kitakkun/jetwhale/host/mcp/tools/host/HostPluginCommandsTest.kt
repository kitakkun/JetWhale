package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostPluginCommandsTest {

    private val officialPluginId = OfficialPluginCatalog.plugins.first().pluginId

    private val loadedPlugins = mutableMapOf(
        "com.example.local" to loadedPlugin("com.example.local", "Local Plugin", requiresAgent = false),
    )
    private val failedJars = MutableStateFlow(listOf(FailedPluginJar("/plugins/broken.jar", "bad manifest")))
    private val untrustedJars = MutableStateFlow(listOf("/plugins/unknown.jar"))
    private val enabledPluginIds = MutableStateFlow(setOf("com.example.local"))
    private val installProgress = MutableStateFlow<PluginInstallProgress?>(null)

    private val pluginFactoryRepository = mock<PluginFactoryRepository> {
        every { this@mock.loadedPlugins } returns this@HostPluginCommandsTest.loadedPlugins
        every { failedJarsFlow } returns failedJars
    }
    private val enabledPluginsRepository = mock<EnabledPluginsRepository>(MockMode.autoUnit) {
        every { enabledPluginIdsFlow } returns enabledPluginIds
    }
    private val pluginTrustService = mock<PluginTrustService> {
        every { untrustedJarPathsFlow } returns untrustedJars
    }
    private val pluginInstanceService = mock<PluginInstanceService> {
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }
    private val pluginInstallProgressRepository = mock<PluginInstallProgressRepository> {
        every { progressFlow } returns installProgress
    }
    private val officialPluginInstallService = mock<OfficialPluginInstallService>(MockMode.autoUnit)

    private val listInstalledPlugins = ListInstalledPluginsCommand(pluginFactoryRepository, enabledPluginsRepository, pluginTrustService)
    private val setPluginEnabled = SetPluginEnabledCommand(
        pluginFactoryRepository,
        enabledPluginsRepository,
        pluginInstanceService,
        mock<DebugSessionRepository> { every { debugSessionsFlow } returns flowOf(persistentListOf()) },
    )
    private val installOfficialPlugin = InstallOfficialPluginCommand(
        officialPluginInstallService,
        pluginFactoryRepository,
        pluginInstallProgressRepository,
    )

    @Test
    fun `listInstalledPlugins reports each plugin's enabled state`() = runBlocking {
        val result = listInstalledPlugins.execute(arguments()).decodeList()

        val plugin = result.installed.single()
        assertEquals("com.example.local", plugin.pluginId)
        assertEquals("Local Plugin", plugin.name)
        assertFalse(plugin.requiresAgent)
        assertTrue(plugin.enabled)
    }

    @Test
    fun `listInstalledPlugins marks an official plugin that is not installed`() = runBlocking {
        val official = listInstalledPlugins.execute(arguments()).decodeList().availableOfficial.single { it.pluginId == officialPluginId }

        assertFalse(official.installed)
    }

    @Test
    fun `listInstalledPlugins marks an official plugin that is already installed`() = runBlocking {
        loadedPlugins[officialPluginId] = loadedPlugin(officialPluginId, "Network Inspector", requiresAgent = true)

        val official = listInstalledPlugins.execute(arguments()).decodeList().availableOfficial.single { it.pluginId == officialPluginId }

        assertTrue(official.installed)
    }

    @Test
    fun `listInstalledPlugins reports failed and untrusted jars`() = runBlocking {
        val result = listInstalledPlugins.execute(arguments()).decodeList()

        assertEquals("/plugins/broken.jar", result.failedJars.single().jarPath)
        assertEquals("/plugins/unknown.jar", result.untrustedJars.single())
    }

    @Test
    fun `setPluginEnabled rejects a pluginId that is not installed`(): Unit = runBlocking {
        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            setPluginEnabled.execute(arguments("pluginId" to JsonPrimitive("com.example.missing"), "enabled" to JsonPrimitive(true)))
        }
        assertContains(error.message.orEmpty(), "is not installed")
    }

    @Test
    fun `setPluginEnabled disables a plugin without waiting for instantiation`() = runBlocking {
        val result = setPluginEnabled
            .execute(arguments("pluginId" to JsonPrimitive("com.example.local"), "enabled" to JsonPrimitive(false)))
            .let { Json.decodeFromString<SetPluginEnabledResult>(it) }

        assertFalse(result.enabled)
        assertFalse(result.reconnectRequiredForNewTools)
        assertTrue(result.instantiatedForSessions.isEmpty())
        verifySuspend { enabledPluginsRepository.setPluginEnabled("com.example.local", false) }
    }

    @Test
    fun `installOfficialPlugin rejects a pluginId that is not in the official catalog`(): Unit = runBlocking {
        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            installOfficialPlugin.execute(arguments("pluginId" to JsonPrimitive("com.evil.backdoor")))
        }
        assertContains(error.message.orEmpty(), "is not an official plugin")
    }

    @Test
    fun `installOfficialPlugin is refused while another installation is in flight`(): Unit = runBlocking {
        installProgress.value = PluginInstallProgress.DownloadingPlugin

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            installOfficialPlugin.execute(arguments("pluginId" to JsonPrimitive(officialPluginId)))
        }
        assertContains(error.message.orEmpty(), "already in progress")
    }

    @Test
    fun `installOfficialPlugin installs the catalog entry and points at the next step`() = runBlocking {
        val result = installOfficialPlugin
            .execute(arguments("pluginId" to JsonPrimitive(officialPluginId)))
            .let { Json.decodeFromString<InstallOfficialPluginResult>(it) }

        assertTrue(result.installed)
        assertFalse(result.alreadyInstalled)
        assertFalse(result.enabled)
        assertEquals("jetwhale.setPluginEnabled", result.nextStep)
        assertTrue(result.reconnectRequiredForNewTools)
        verifySuspend { officialPluginInstallService.install(OfficialPluginCatalog.plugins.first()) }
    }

    @Test
    fun `installOfficialPlugin reports an already installed plugin without reinstalling it`() = runBlocking {
        loadedPlugins[officialPluginId] = loadedPlugin(officialPluginId, "Network Inspector", requiresAgent = true)

        val result = installOfficialPlugin
            .execute(arguments("pluginId" to JsonPrimitive(officialPluginId)))
            .let { Json.decodeFromString<InstallOfficialPluginResult>(it) }

        assertTrue(result.alreadyInstalled)
    }
}

private fun loadedPlugin(pluginId: String, name: String, requiresAgent: Boolean) = LoadedHostPlugin(
    manifest = JetWhaleHostPluginManifest(
        pluginId = pluginId,
        pluginName = name,
        version = "1.0.0",
        factoryClass = "$pluginId.Factory",
        requiresAgent = requiresAgent,
    ),
    factory = object : JetWhaleHostPluginFactory {
        override fun createPlugin(): JetWhaleHostPlugin = throw UnsupportedOperationException()
    },
)

private fun arguments(vararg entries: Pair<String, JsonPrimitive>) = JetWhaleMcpArguments(JsonObject(entries.toMap()))

private fun String.decodeList() = Json.decodeFromString<ListInstalledPluginsResult>(this)
