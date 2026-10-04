package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TimelinePaneTest {
    @Test
    fun `a platform that cannot time tasks says why instead of showing an empty timeline`() = runComposeUiTest {
        val unsupported = report(hotspots = emptyList(), violations = emptyList(), longTasks = emptyList()).copy(
            capabilities = MonitorCapabilities(platform = "Web", taskTiming = false, stackSampling = false, strictMode = false, frameTiming = false, note = "Main-thread monitoring is not available on the web yet."),
        )
        setContent {
            JwTheme(darkTheme = false) {
                TimelinePane(unsupported)
            }
        }

        onNodeWithText("Main-thread monitoring is not available on the web yet.").assertExists()
        onAllNodesWithText("No long tasks").assertCountEquals(0)
    }
}
