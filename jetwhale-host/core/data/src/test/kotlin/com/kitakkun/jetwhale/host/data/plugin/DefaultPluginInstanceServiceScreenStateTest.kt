package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneFactory
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginScreenState
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A plugin screen follows one state, published by the service that owns the plugin's instance and
 * the scene composing it, so the scene can never outlive the instance it belongs to.
 */
@OptIn(InternalComposeUiApi::class)
class DefaultPluginInstanceServiceScreenStateTest {
    private val pluginId = "com.example.plugin"
    private val sessionId = "session-1"

    private val storage = mock<JetWhalePluginStorage>()
    private val dataStoreRepository = mock<PluginDataStoreRepository> {
        every { storageFor(any()) } returns storage
    }
    private val frameSender = mock<HostPluginFrameSender>()
    private val sceneFactory = RecordingSceneFactory()

    @Test
    fun `a screen opened before its instance exists shows starting and then the scene`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { UiPlugin() })
        val screenState = service.pluginScreenStateFlow(pluginId, sessionId)
        assertEquals(PluginScreenState.Starting, screenState.first())

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        val ready = assertIs<PluginScreenState.Ready>(awaitReady(screenState))
        assertSame(sceneFactory.created.single(), ready.scene)
    }

    @Test
    fun `a plugin whose creation throws shows the failure`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { error("factory broke") })

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        val failed = assertIs<PluginScreenState.Failed>(service.pluginScreenStateFlow(pluginId, sessionId).first())
        assertEquals("factory broke", failed.cause.message)
    }

    @Test
    fun `a failed start is forgotten once the plugin is unloaded`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { error("factory broke") })
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        service.unloadPluginInstancesForPlugin(pluginId)

        assertEquals(PluginScreenState.Starting, service.pluginScreenStateFlow(pluginId, sessionId).first())
    }

    @Test
    fun `a replaced instance gets a new scene and its old scene is closed`() = runBlocking<Unit> {
        val repository = FakePluginFactoryRepository(loadedPlugin(factoryOf { UiPlugin() }))
        val service = serviceWith(repository)
        val screenState = service.pluginScreenStateFlow(pluginId, sessionId)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        val replacedScene = awaitReady(screenState).scene

        // A reinstall serves the plugin from a new factory, whose instances replace the old ones.
        repository.loaded = loadedPlugin(factoryOf { UiPlugin() })
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        val replacementScene = awaitReady(screenState).scene
        assertNotSame(replacedScene, replacementScene)
        assertSame(replacementScene, service.getOrCreatePluginScene(pluginId, sessionId))
        awaitClosed(replacedScene)
    }

    @Test
    fun `unloading an instance closes its scene`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { UiPlugin() })
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        val scene = awaitReady(service.pluginScreenStateFlow(pluginId, sessionId)).scene

        service.unloadPluginInstanceForSession(sessionId)

        awaitClosed(scene)
        assertNull(service.getOrCreatePluginScene(pluginId, sessionId))
    }

    @Test
    fun `an in-place reload gives the instance a new scene and closes the old one`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { UiPlugin() })
        val screenState = service.pluginScreenStateFlow(pluginId, sessionId)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        val instance = service.getPluginInstanceForSession(pluginId, sessionId)
        val oldScene = awaitReady(screenState).scene

        service.recreatePluginScenes(pluginId)

        assertNotSame(oldScene, awaitReady(screenState).scene)
        assertSame(instance, service.getPluginInstanceForSession(pluginId, sessionId))
        awaitClosed(oldScene)
    }

    @Test
    fun `a plugin with no UI shows the headless screen and never gets a scene`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { object : JetWhaleHostPlugin() {} })

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        assertEquals(PluginScreenState.Headless, service.pluginScreenStateFlow(pluginId, sessionId).first())
        assertNull(service.getOrCreatePluginScene(pluginId, sessionId))
        assertTrue(sceneFactory.created.isEmpty())
    }

    @Test
    fun `content that throws while its scene is first composed fails the screen until it is retried`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { UiPlugin() })
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))
        sceneFactory.failNext = IllegalStateException("content broke")

        val failed = assertIs<PluginScreenState.Failed>(service.pluginScreenStateFlow(pluginId, sessionId).first())

        assertEquals("content broke", failed.cause.message)
        assertIs<PluginScreenState.Ready>(service.pluginScreenStateFlow(pluginId, sessionId).first())
    }

    @Test
    fun `the screen stops showing an instance before its onDispose runs`() = runBlocking<Unit> {
        var stateDuringDispose: PluginScreenState? = null
        lateinit var service: DefaultPluginInstanceService
        service = serviceWith(
            factoryOf {
                object : JetWhaleHostPlugin() {
                    override fun onDispose() {
                        stateDuringDispose = runBlocking { service.pluginScreenStateFlow(pluginId, sessionId).first() }
                    }
                }
            },
        )
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId))

        service.unloadPluginInstanceForSession(sessionId)

        assertEquals(PluginScreenState.Starting, stateDuringDispose)
    }

    @Test
    fun `a publication read before another thread's change does not overwrite that change`() = runBlocking<Unit> {
        val service = serviceWith(factoryOf { object : JetWhaleHostPlugin() {} })
        val firstPublicationHalfway = CountDownLatch(1)
        val secondChangeDone = CountDownLatch(1)
        // An unconfined collector runs inside the publishing thread's assignment, holding that thread
        // after it has read the instances and published the headless set, before it publishes the
        // screen states.
        val holdFirstPublication = launch(Dispatchers.Unconfined) {
            service.headlessPluginsFlow.first { it.pluginIdsBySession.isNotEmpty() }
            firstPublicationHalfway.countDown()
            // Bounded, because a publication that waits for the other one to finish never sees it.
            secondChangeDone.await(500, TimeUnit.MILLISECONDS)
        }

        val first = thread { service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(sessionId)) }
        firstPublicationHalfway.await()
        val second = thread {
            service.initializePluginInstancesForSessionsIfNeeded(pluginId, setOf(HostSession.ID))
            secondChangeDone.countDown()
        }
        first.join()
        second.join()
        holdFirstPublication.join()

        assertEquals(PluginScreenState.Headless, service.pluginScreenStateFlow(pluginId, HostSession.ID).first())
    }

    private suspend fun awaitReady(screenState: Flow<PluginScreenState>): PluginScreenState.Ready = withTimeout(TIMEOUT_MILLIS) {
        screenState.first { it is PluginScreenState.Ready } as PluginScreenState.Ready
    }

    // Scenes close on the main thread, after the instance is already gone.
    private suspend fun awaitClosed(scene: PluginComposeScene) = withTimeout(TIMEOUT_MILLIS) {
        sceneFactory.closed.first { scene in it }
    }

    private fun serviceWith(factory: JetWhaleHostPluginFactory) = serviceWith(FakePluginFactoryRepository(loadedPlugin(factory)))

    private fun serviceWith(repository: PluginFactoryRepository) = DefaultPluginInstanceService(
        pluginFactoryRepository = repository,
        frameSender = frameSender,
        pluginDataStoreRepository = dataStoreRepository,
        pluginComposeSceneFactory = sceneFactory,
    )

    private fun factoryOf(createPlugin: () -> JetWhaleHostPlugin) = object : JetWhaleHostPluginFactory {
        override fun createPlugin(): JetWhaleHostPlugin = createPlugin()
    }

    private fun loadedPlugin(factory: JetWhaleHostPluginFactory) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = pluginId,
            pluginName = "Test",
            version = "1.0.0",
            factoryClass = "com.example.TestFactory",
            requiresAgent = false,
        ),
        factory = factory,
    )

    private class UiPlugin :
        JetWhaleHostPlugin(),
        JetWhaleHostPluginUi {
        @Composable
        override fun Content() = Unit
    }

    private class RecordingSceneFactory : PluginComposeSceneFactory {
        val created: MutableList<PluginComposeScene> = CopyOnWriteArrayList()

        /** Scenes whose composition was disposed, which is what closing a scene does. */
        val closed = MutableStateFlow<Set<PluginComposeScene>>(emptySet())

        /** Thrown by the next scene creation, as content that throws while it is first composed would be. */
        @Volatile
        var failNext: Throwable? = null

        override fun updateHostDensity(density: Density) = Unit

        override fun createScene(plugin: JetWhaleHostPlugin, content: @Composable () -> Unit): PluginComposeScene {
            failNext?.let { failure ->
                failNext = null
                throw failure
            }
            lateinit var scene: PluginComposeScene
            val composeScene = CanvasLayersComposeScene()
            composeScene.setContent {
                DisposableEffect(Unit) {
                    onDispose { closed.update { it + scene } }
                }
            }
            scene = PluginComposeScene(
                composeScene = composeScene,
                windowInfoUpdater = mock(),
                semanticsOwners = emptySet(),
                isMcpCapture = mutableStateOf(false),
                pointerIcon = mutableStateOf(PointerIcon.Default),
            )
            created += scene
            return scene
        }
    }

    private class FakePluginFactoryRepository(var loaded: LoadedHostPlugin) : PluginFactoryRepository {
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = mapOf(loaded.manifest.pluginId to loaded)
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(loadedPlugins)
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
