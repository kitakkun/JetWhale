package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalAiOperatedUiTargets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class JwAiOperatingRingTest {
    @Test
    fun `a control is ringed only while an agent operates its target`() = runDesktopComposeUiTest(width = 100, height = 100) {
        mainClock.autoAdvance = false
        var operatedUiTargets by mutableStateOf(emptySet<Any>())
        setContent {
            JwTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAiOperatedUiTargets provides operatedUiTargets) {
                    Box(
                        Modifier
                            .testTag(CONTROL_TAG)
                            .size(40.dp)
                            .background(Color.White)
                            .jwAiOperatingRingWhileOperated(TestUiTarget.Row(id = "a"), shape = JwShapes.small),
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()
        assertFalse(isControlRinged())

        operatedUiTargets = setOf(TestUiTarget.List, TestUiTarget.Row(id = "a"))
        mainClock.advanceTimeByFrame()
        assertTrue(isControlRinged())

        operatedUiTargets = setOf(TestUiTarget.List, TestUiTarget.Row(id = "b"))
        mainClock.advanceTimeByFrame()
        assertFalse(isControlRinged())
    }

    @Test
    fun `a control is not ringed outside the host, where no operated targets are provided`() = runDesktopComposeUiTest(width = 100, height = 100) {
        mainClock.autoAdvance = false
        setContent {
            JwTheme(darkTheme = false) {
                Box(
                    Modifier
                        .testTag(CONTROL_TAG)
                        .size(40.dp)
                        .background(Color.White)
                        .jwAiOperatingRingWhileOperated(TestUiTarget.List, shape = JwShapes.small),
                )
            }
        }
        mainClock.advanceTimeByFrame()

        assertFalse(isControlRinged())
    }

    /** Whether anything but the control's own white background is drawn over it. */
    private fun ComposeUiTest.isControlRinged(): Boolean {
        val pixels = onNodeWithTag(CONTROL_TAG).captureToImage().toPixelMap()
        return (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] != Color.White } }
    }

    private sealed interface TestUiTarget {
        data object List : TestUiTarget

        data class Row(val id: String) : TestUiTarget
    }

    private companion object {
        const val CONTROL_TAG = "control"
    }
}
