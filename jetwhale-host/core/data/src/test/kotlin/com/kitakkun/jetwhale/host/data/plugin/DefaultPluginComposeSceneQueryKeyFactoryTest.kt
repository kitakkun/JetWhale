package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginInstanceState
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import soil.query.QueryReceiver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class DefaultPluginComposeSceneQueryKeyFactoryTest {
    private val instanceState = MutableStateFlow<PluginInstanceState>(PluginInstanceState.Absent)
    private val instanceService = mock<PluginInstanceService> {
        every { pluginInstanceStateFlow(any(), any()) } returns instanceState
    }

    @Test
    fun `the scene waits for an instance and for its replacement when the first one is gone before its scene is built`() = runTest {
        val scene = emptyScene()
        val sceneService = SceneServiceAnswering(null, scene)
        val key = DefaultPluginComposeSceneQueryKeyFactory(sceneService, instanceService).create(pluginId = "com.example.plugin", sessionId = "host")

        val fetching = async { key.fetch(QueryReceiver) }
        runCurrent()
        assertEquals(0, sceneService.calls, "asked for a scene before there was an instance")

        instanceState.value = PluginInstanceState.Running(object : JetWhaleHostPlugin() {})
        runCurrent()
        assertEquals(1, sceneService.calls, "asked again for the instance it just failed on")

        instanceState.value = PluginInstanceState.Running(object : JetWhaleHostPlugin() {})
        assertSame(scene, fetching.await())
        scene.composeScene.close()
    }

    @Test
    fun `a plugin that failed to start fails the scene with the reason`() = runTest {
        instanceState.value = PluginInstanceState.FailedToStart(IllegalStateException("factory broke"))
        val key = DefaultPluginComposeSceneQueryKeyFactory(SceneServiceAnswering(), instanceService).create(pluginId = "com.example.plugin", sessionId = "host")

        val error = assertFailsWith<IllegalStateException> { key.fetch(QueryReceiver) }

        assertTrue("factory broke" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a plugin that never starts fails the scene instead of waiting forever`() = runTest {
        val key = DefaultPluginComposeSceneQueryKeyFactory(SceneServiceAnswering(), instanceService).create(pluginId = "com.example.plugin", sessionId = "host")

        val error = assertFailsWith<IllegalStateException> { key.fetch(QueryReceiver) }

        assertTrue("didn't start" in error.message.orEmpty(), error.message)
    }

    private fun emptyScene(): PluginComposeScene = PluginComposeScene(
        composeScene = CanvasLayersComposeScene(),
        windowInfoUpdater = object : WindowInfoUpdater {
            override val currentIntSize: IntSize = IntSize.Zero
            override val currentDpSize: DpSize = DpSize.Zero
            override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) = Unit
        },
        semanticsOwners = emptySet(),
        isMcpCapture = mutableStateOf(false),
        pointerIcon = mutableStateOf(PointerIcon.Default),
    )

    /** Answers each request for a scene with the next of [answers]. */
    private class SceneServiceAnswering(vararg answers: PluginComposeScene?) : PluginComposeSceneService {
        private val remaining = ArrayDeque(answers.toList())
        var calls = 0
            private set

        override fun updateHostDensity(density: Density) = Unit
        override suspend fun getOrCreatePluginScene(pluginId: String, sessionId: String): PluginComposeScene? {
            calls++
            return remaining.removeFirst()
        }
        override fun disposePluginSceneForSession(sessionId: String) = Unit
        override fun disposePluginScenesForPlugin(pluginId: String) = Unit
        override fun disposeAppSessionPluginScenes() = Unit
    }
}
