package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import com.kitakkun.jetwhale.host.ui.JwTheme
import soil.plant.compose.reacty.ErrorBoundary
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class PluginScreenFailureTest {
    @Test
    fun `a scene that fails after it is shown gives way to the error fallback and its Reload`() = runComposeUiTest {
        // The screen keeps asking for frames to drive the plugin's animations, so the test clock
        // never goes idle on its own; frames are stepped by hand instead.
        mainClock.autoAdvance = false
        val scene = pluginScene()
        setContent {
            JwTheme(darkTheme = false) {
                ErrorBoundary(fallback = { PluginScreenErrorFallback(pluginId = "com.example.plugin", errorBoundaryContext = it, onClickReset = {}) }) {
                    PluginScreen(scene)
                }
            }
        }
        repeat(FRAMES) { mainClock.advanceTimeByFrame() }
        onNodeWithText("Reload").assertDoesNotExist()

        scene.failure.value = IllegalStateException("effect boom")
        repeat(FRAMES) { mainClock.advanceTimeByFrame() }

        onNodeWithText("Reload").assertExists()
    }

    @Test
    fun `a scene that had already failed when it arrives waits for its replacement instead of reopening the fallback`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val stale = pluginScene().apply { failure.value = IllegalStateException("effect boom") }
        val fresh = pluginScene()
        var shown by mutableStateOf(stale)
        setContent {
            JwTheme(darkTheme = false) {
                ErrorBoundary(fallback = { PluginScreenErrorFallback(pluginId = "com.example.plugin", errorBoundaryContext = it, onClickReset = {}) }) {
                    PluginScreen(shown)
                }
            }
        }
        repeat(FRAMES) { mainClock.advanceTimeByFrame() }

        shown = fresh
        repeat(FRAMES) { mainClock.advanceTimeByFrame() }

        onNodeWithText("Reload").assertDoesNotExist()
    }
}

@OptIn(InternalComposeUiApi::class)
private fun pluginScene() = PluginComposeScene(
    composeScene = CanvasLayersComposeScene(platformContext = PlatformContext.Empty()),
    windowInfoUpdater = NoWindow,
    semanticsOwners = emptySet(),
    isMcpCapture = mutableStateOf(false),
    pointerIcon = mutableStateOf(PointerIcon.Default),
    failure = mutableStateOf(null),
)

private const val FRAMES = 3

private object NoWindow : WindowInfoUpdater {
    override val currentIntSize: IntSize = IntSize.Zero
    override val currentDpSize: DpSize = DpSize.Zero

    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) = Unit
}
