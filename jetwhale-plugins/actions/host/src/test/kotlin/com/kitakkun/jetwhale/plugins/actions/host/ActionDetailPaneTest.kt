package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ActionDetailPaneTest {
    private val signIn = action("Sign in", destructive = false, parameters = listOf(parameter("email", ParameterType.STRING, optional = false, nullable = false)))

    @Test
    fun `last arguments that load after the first frame fill the form`() = runComposeUiTest {
        // rememberPersistent starts from its default and delivers the stored value a moment later.
        var remembered by mutableStateOf<JsonObject?>(null)
        setContent {
            JwTheme(darkTheme = false) {
                ActionDetailPane(action = signIn, options = emptyMap(), rememberedArguments = remembered, runs = emptyList(), onRun = { _, _ -> }, onCancel = {})
            }
        }

        remembered = JsonObject(mapOf("email" to JsonPrimitive("qa@example.com")))
        waitForIdle()

        onNode(hasSetTextAction()).assertTextContains("qa@example.com")
    }
}
