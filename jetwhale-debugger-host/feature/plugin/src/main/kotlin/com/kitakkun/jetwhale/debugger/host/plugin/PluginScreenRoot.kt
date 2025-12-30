package com.kitakkun.jetwhale.debugger.host.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.debugger.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.debugger.host.architecture.SoilFallbackDefaults
import soil.query.compose.rememberQuery

@OptIn(InternalComposeUiApi::class)
context(screenContext: PluginScreenContext)
@Composable
fun PluginScreenRoot() {
    var reset by remember { mutableStateOf(false) }

    key(reset) {
        SoilDataBoundary(
            state = rememberQuery(screenContext.pluginComposeSceneQueryKey),
            fallback = SoilFallbackDefaults.custom(
                suspenseFallback = SoilFallbackDefaults.default().suspenseFallback,
                errorFallback = {
                    PluginScreenErrorFallback(
                        pluginId = screenContext.pluginId,
                        errorBoundaryContext = it,
                        // force recompose when reset is clicked
                        onClickReset = { reset = !reset },
                    )
                },
            )
        ) { pluginComposeScene ->
            PluginScreen(pluginComposeScene = pluginComposeScene)
        }
    }
}
