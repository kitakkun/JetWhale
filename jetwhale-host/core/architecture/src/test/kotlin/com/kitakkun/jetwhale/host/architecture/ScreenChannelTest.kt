package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ScreenChannelTest {
    private object TestPresenterContext : PresenterContext
    private object TestScreenContext : ScreenContext

    @Test
    fun `an action sent after the presenter recomposes reaches the handler of the latest composition`() = runComposeUiTest {
        val channel = ScreenChannel<String, Nothing>()
        val received = mutableListOf<String>()
        var handlerName by mutableStateOf("first")
        setContent {
            val name = handlerName
            context(TestPresenterContext) {
                ActionEffect(channel) { action -> received += "$name:$action" }
            }
        }
        waitForIdle()

        handlerName = "second"
        waitForIdle()
        context(TestScreenContext) { channel.send("refresh") }
        waitUntil { received.isNotEmpty() }

        assertEquals(listOf("second:refresh"), received)
    }
}
