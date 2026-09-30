package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.mutableStateOf
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
        val scene = PluginComposeScene(
            composeScene = CanvasLayersComposeScene(platformContext = PlatformContext.Empty()),
            windowInfoUpdater = NoWindow,
            semanticsOwners = emptySet(),
            isMcpCapture = mutableStateOf(false),
            pointerIcon = mutableStateOf(PointerIcon.Default),
            failure = mutableStateOf(null),
        )
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
}

private const val FRAMES = 3

private object NoWindow : WindowInfoUpdater {
    override val currentIntSize: IntSize = IntSize.Zero
    override val currentDpSize: DpSize = DpSize.Zero

    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) = Unit
}
