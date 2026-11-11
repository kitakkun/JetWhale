package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalAiOperatedUiTargets
import com.kitakkun.jetwhale.host.ui.JwColors
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class MirrorScreenTest {
    @Test
    fun `the live view between the toolbar and the text input is ringed while an agent operates it`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var operatedUiTargets by mutableStateOf(emptySet<Any>())
        setContent {
            PluginSceneSurface(darkTheme = false, storage = InMemoryPluginStorage(emptyMap())) {
                CompositionLocalProvider(LocalAiOperatedUiTargets provides operatedUiTargets) {
                    Box(Modifier.requiredSize(width = 800.dp, height = 600.dp)) {
                        MirrorScreen(
                            devices = listOf(emulator),
                            capabilities = DeviceCapabilities(inputRefusal = null, buttons = listOf(DeviceButton.Home), recording = true, screenPower = false),
                            missingTools = emptyList(),
                            selectedId = emulator.id,
                            state = MirrorState.Streaming,
                            notices = IgnoredNotices,
                            screenPower = null,
                            recordingSinceMillis = null,
                            surface = remember(::MirrorSurface),
                            actions = NoMirrorActions,
                            showCaptures = false,
                            livenessOf = { DeviceLiveness.Live },
                            onToggleCaptures = {},
                            onShowGrid = {},
                            capturesPanel = {},
                            modifier = Modifier.testTag(MIRROR_SCREEN_TAG),
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        assertEquals(emptyList(), accentPixelPositions(), "nothing is ringed before the screen is operated")

        operatedUiTargets = setOf(MirrorUiTarget.LiveView)
        mainClock.advanceTimeByFrame()

        val ringPixelPositions = accentPixelPositions()
        val toolbarBottom = onNodeWithContentDescription("Screenshot").fetchSemanticsNode().boundsInRoot.bottom
        val textInputTop = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.top
        val mirrorScreenBounds = onNodeWithTag(MIRROR_SCREEN_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(ringPixelPositions.isNotEmpty(), "the live view is not ringed")
        assertTrue(ringPixelPositions.all { (_, y) -> y >= toolbarBottom && y < textInputTop }, "the ring leaves the live view")
        assertTrue(ringPixelPositions.any { (x, _) -> x < mirrorScreenBounds.left + EDGE_WIDTH_PX } && ringPixelPositions.any { (x, _) -> x >= mirrorScreenBounds.right - EDGE_WIDTH_PX }, "the ring does not span the screen's width")
    }

    /** Where the AI accent shows: nothing else on the screen is drawn in it. */
    private fun ComposeUiTest.accentPixelPositions(): List<Pair<Int, Int>> {
        val pixels: PixelMap = onRoot().captureToImage().toPixelMap()
        val aiAccent = JwColors.light().aiAccent
        return buildList {
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) {
                    val pixel = pixels[x, y]
                    if (abs(pixel.red - aiAccent.red) < ACCENT_TOLERANCE && abs(pixel.green - aiAccent.green) < ACCENT_TOLERANCE && abs(pixel.blue - aiAccent.blue) < ACCENT_TOLERANCE) add(x to y)
                }
            }
        }
    }

    private val emulator = DeviceListing(id = "emulator-5554", name = "Pixel 9", kind = DeviceKind.AndroidEmulator, osVersion = null)

    private companion object {
        /** Close enough to count a pixel where the ring's highlight peaks, far enough to tell it from the screen's greys. */
        const val ACCENT_TOLERANCE = 0.1f

        /** Covers the ring's stroke, which lies just inside the ringed area's bounds. */
        const val EDGE_WIDTH_PX = 3

        const val MIRROR_SCREEN_TAG = "mirror-screen"
    }
}
