package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import com.kitakkun.jetwhale.host.model.PluginScreenState
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import soil.query.SubscriptionId
import soil.query.SwrCachePlus
import soil.query.SwrCachePlusPolicy
import soil.query.buildSubscriptionKey
import soil.query.compose.SwrClientProvider
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class PluginScreenRootTest {
    @Test
    fun `a click the plugin throws on shows its crash screen until Reload`() = runComposeUiTest {
        showPluginScreenRoot(MutableStateFlow(PluginScreenState.Ready(throwingOnClickScene())))

        onRoot().performMouseInput { click(center) }
        advanceFrames()
        onNodeWithText("Plugin UI Crashed").assertExists()

        onNodeWithText("Reload").performClick()
        advanceFrames()
        onNodeWithText("Plugin UI Crashed").assertDoesNotExist()
    }

    @Test
    fun `a new scene replaces the crash screen of the scene before it`() = runComposeUiTest {
        val states = MutableStateFlow<PluginScreenState>(PluginScreenState.Ready(throwingOnClickScene()))
        showPluginScreenRoot(states)
        onRoot().performMouseInput { click(center) }
        advanceFrames()
        onNodeWithText("Plugin UI Crashed").assertExists()

        states.value = PluginScreenState.Ready(pluginScene { Box(Modifier.fillMaxSize()) })
        advanceFrames()

        onNodeWithText("Plugin UI Crashed").assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.showPluginScreenRoot(states: MutableStateFlow<out PluginScreenState>) {
    // The plugin screen asks for a frame on every frame to drive the plugin's animations, so the
    // test clock never goes idle on its own; frames are stepped by hand instead.
    mainClock.autoAdvance = false
    val screenContext = PluginScreenContext(
        pluginId = "com.example.plugin",
        sessionId = "session",
        pluginScreenStateSubscriptionKeyFactory = { _, _ ->
            buildSubscriptionKey(id = SubscriptionId("plugin-screen-state"), subscribe = { states })
        },
        pluginJarSwapService = NoJarSwaps,
    )
    setContent {
        SwrClientProvider(SwrCachePlus(SwrCachePlusPolicy(CoroutineScope(Dispatchers.Unconfined + SupervisorJob())))) {
            JwTheme(darkTheme = false) {
                context(screenContext) {
                    PluginScreenRoot()
                }
            }
        }
    }
    advanceFrames()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.advanceFrames() {
    repeat(3) { mainClock.advanceTimeByFrame() }
}

private fun throwingOnClickScene(): PluginComposeScene = pluginScene {
    Box(Modifier.fillMaxSize().clickable { throw IllegalStateException("The plugin's click handler failed") })
}

@OptIn(InternalComposeUiApi::class)
private fun pluginScene(content: @Composable () -> Unit): PluginComposeScene {
    val composeScene = CanvasLayersComposeScene(platformContext = PlatformContext.Empty())
    composeScene.setContent(content)
    return PluginComposeScene(
        composeScene = composeScene,
        windowInfoUpdater = NoWindow,
        semanticsOwners = emptySet(),
        isMcpCapture = mutableStateOf(false),
        pointerIcon = mutableStateOf(PointerIcon.Default),
    )
}

private object NoWindow : WindowInfoUpdater {
    override val currentIntSize: IntSize = IntSize.Zero
    override val currentDpSize: DpSize = DpSize.Zero

    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) = Unit
}

private object NoJarSwaps : PluginJarSwapService {
    override val pluginReloadedFlow: SharedFlow<String> = MutableSharedFlow()
    override suspend fun hotSwap(jarPath: String) = Unit
    override suspend fun reload(jarPath: String, expectedSha256: String?) = Unit
    override suspend fun remove(jarPath: String) = Unit
}
