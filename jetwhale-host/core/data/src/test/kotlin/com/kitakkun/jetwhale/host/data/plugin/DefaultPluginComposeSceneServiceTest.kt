package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.RunningMcpCommandsService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalComposeUiApi::class, InternalJetWhaleHostApi::class, ExperimentalJetWhaleApi::class)
class DefaultPluginComposeSceneServiceTest {
    private val plugin = WindowSizeReadingPlugin().apply { bindStorage(mock()) }

    private val runningMcpCommandsService = FakeRunningMcpCommandsService()

    private val service = DefaultPluginComposeSceneService(
        pluginBridgeProvider = PassThroughBridgeProvider,
        pluginInstanceService = mock<PluginInstanceService> {
            every { getPluginInstanceForSession(any(), any()) } returns plugin
        },
        runningMcpCommandsService = runningMcpCommandsService,
    )

    @Test
    fun `an unchanged window size does not recompose the plugin UI`() = runBlocking {
        val scene = service.getOrCreatePluginScene(pluginId = "plugin", sessionId = "session")
        withContext(Dispatchers.Main) {
            scene.showAt(IntSize(400, 300))
            val compositionsBefore = plugin.compositions

            scene.showAt(IntSize(400, 300))

            assertEquals(compositionsBefore, plugin.compositions)
        }
    }

    @Test
    fun `a changed window size reaches the plugin UI`() = runBlocking {
        val scene = service.getOrCreatePluginScene(pluginId = "plugin", sessionId = "session")
        withContext(Dispatchers.Main) {
            scene.showAt(IntSize(400, 300))

            scene.showAt(IntSize(800, 600))

            assertEquals(IntSize(800, 600), plugin.observedContainerSize)
        }
    }

    @Test
    fun `a plugin UI sees which of its own MCP commands an agent is running`() = runBlocking {
        val scene = service.getOrCreatePluginScene(pluginId = "plugin", sessionId = "session")
        withContext(Dispatchers.Main) {
            scene.showAt(IntSize(400, 300))

            runningMcpCommandsService.runningCommandNames.value = setOf(SwipeCommand.name)
            scene.showAt(IntSize(400, 300))

            assertEquals(setOf(SwipeCommand.name), plugin.observedRunningMcpCommandNames)
        }
    }

    @Test
    fun `only the plugin instance's own MCP commands are looked for`() = runBlocking {
        service.getOrCreatePluginScene(pluginId = "plugin", sessionId = "session")

        assertEquals(PluginInstanceCommandNames(pluginId = "plugin", sessionId = "session", commandNames = setOf(TapCommand.name, SwipeCommand.name)), runningMcpCommandsService.requestedCommandNames)
    }

    private fun PluginComposeScene.showAt(size: IntSize) {
        composeScene.size = size
        windowInfoUpdater.updateWindowSize(intSize = size, dpSize = DpSize(size.width.dp, size.height.dp))
        Snapshot.sendApplyNotifications()
        render(Canvas(ImageBitmap(1, 1)))
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class WindowSizeReadingPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {
    var compositions = 0
    var observedContainerSize: IntSize? = null
    var observedRunningMcpCommandNames: Set<String>? = null

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(TapCommand, SwipeCommand)

    @Composable
    override fun Content() {
        val containerSize = LocalWindowInfo.current.containerSize
        val runningMcpCommandNames = LocalRunningMcpCommandNames.current
        SideEffect {
            compositions++
            observedContainerSize = containerSize
            observedRunningMcpCommandNames = runningMcpCommandNames
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private object TapCommand : JetWhaleMcpCommand() {
    override val name = "com.example.tap"
    override val description = "Taps."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = ""
}

@OptIn(ExperimentalJetWhaleApi::class)
private object SwipeCommand : JetWhaleMcpCommand() {
    override val name = "com.example.swipe"
    override val description = "Swipes."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = ""
}

private data class PluginInstanceCommandNames(val pluginId: String, val sessionId: String, val commandNames: Set<String>)

private class FakeRunningMcpCommandsService : RunningMcpCommandsService {
    val runningCommandNames = MutableStateFlow(emptySet<String>())
    var requestedCommandNames: PluginInstanceCommandNames? = null

    override fun runningCommandNamesFlow(pluginId: String, sessionId: String, commandNames: Set<String>): Flow<Set<String>> {
        requestedCommandNames = PluginInstanceCommandNames(pluginId, sessionId, commandNames)
        return runningCommandNames
    }
}

private object PassThroughBridgeProvider : DynamicPluginBridgeProvider {
    @Composable
    override fun PluginEntryPoint(content: @Composable () -> Unit) {
        content()
    }
}
