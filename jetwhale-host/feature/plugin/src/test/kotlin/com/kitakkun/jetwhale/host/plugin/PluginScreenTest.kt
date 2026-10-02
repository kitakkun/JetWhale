package com.kitakkun.jetwhale.host.plugin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class PluginScreenTest {
    @Test
    fun `a density change alone reaches the plugin's scene in both directions`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val scene = pluginScene()
        var windowDensity by mutableStateOf(Density(1f))
        setContent {
            CompositionLocalProvider(LocalDensity provides windowDensity) {
                PluginScreen(scene)
            }
        }
        renderFrames()
        val sizeBefore = scene.composeScene.size

        windowDensity = Density(2f)
        renderFrames()

        assertEquals(sizeBefore, scene.composeScene.size, "the change must not have come with a new size")
        assertEquals(Density(2f), scene.composeScene.density)

        windowDensity = Density(1f)
        renderFrames()

        assertEquals(Density(1f), scene.composeScene.density)
    }

    @Test
    fun `a scene swapped in under the same screen gets its density and size at once`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val first = pluginScene()
        val second = pluginScene()
        var shown by mutableStateOf(first)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                PluginScreen(shown)
            }
        }
        renderFrames()

        shown = second
        renderFrames()

        assertEquals(Density(2f), second.composeScene.density)
        assertEquals(assertNotNull(first.composeScene.size), second.composeScene.size)
    }
}

// The screen keeps asking for frames to drive the plugin's animations, so the test clock never goes
// idle on its own; frames are stepped by hand instead.
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.renderFrames() {
    repeat(3) { mainClock.advanceTimeByFrame() }
}

@OptIn(InternalComposeUiApi::class)
private fun pluginScene(): PluginComposeScene {
    val platformContext = TestPlatformContext()
    return PluginComposeScene(
        composeScene = CanvasLayersComposeScene(platformContext = platformContext),
        windowInfoUpdater = platformContext,
        semanticsOwners = emptySet(),
        isMcpCapture = mutableStateOf(false),
        pointerIcon = mutableStateOf(PointerIcon.Default),
    )
}

@OptIn(InternalComposeUiApi::class)
private class TestPlatformContext :
    PlatformContext by PlatformContext.Empty(),
    WindowInfoUpdater {
    override var currentIntSize: IntSize = IntSize.Zero
        private set
    override var currentDpSize: DpSize = DpSize.Zero
        private set

    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) {
        currentIntSize = intSize
        currentDpSize = dpSize
    }
}
